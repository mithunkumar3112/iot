package com.iotmonitor.service;

import com.iotmonitor.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Authenticates the agent against the backend's /auth/email-login endpoint
 * and provides a thread-safe Bearer token for all subsequent requests.
 *
 * Call {@link #ensureAuthenticated()} before any HTTP request; if the token
 * is missing the method will block until login succeeds or throws.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AgentConfig config;
    private final RestTemplate rest;

    private volatile String token = null;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public AuthService(AgentConfig config) {
        this.config = config;
        this.rest   = new RestTemplate();
        System.out.println("🔐 AuthService initialized with backend: " + config.getBackendUrl());
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns HTTP headers containing the current Bearer token.
     * Automatically re-authenticates if the token is null.
     */
    public HttpHeaders authHeaders() {
        ensureAuthenticated();
        HttpHeaders headers = new HttpHeaders();
        lock.readLock().lock();
        try {
            headers.setBearerAuth(token);
        } finally {
            lock.readLock().unlock();
        }
        return headers;
    }

    /**
     * Invalidates the cached token so the next call to {@link #authHeaders()}
     * triggers a fresh login. Call this when a 401 is received.
     */
    public void invalidateToken() {
        lock.writeLock().lock();
        try { token = null; }
        finally { lock.writeLock().unlock(); }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    /**
     * Blocks until a valid token is available, retrying every 10 s on failure.
     */
    public void ensureAuthenticated() {
        lock.readLock().lock();
        try {
            if (token != null) return;
        } finally {
            lock.readLock().unlock();
        }
        login();
    }

    @SuppressWarnings("unchecked")
    private void login() {
        String url = config.getBackendUrl() + "/auth/email-login";
        Map<String, String> body = Map.of(
            "email",    config.getEmail(),
            "password", config.getPassword()
        );

        while (true) {
            try {
                ResponseEntity<Map> resp = rest.postForEntity(
                    url,
                    new HttpEntity<>(body, jsonHeaders()),
                    Map.class
                );
                if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                    String newToken = (String) resp.getBody().get("token");
                    lock.writeLock().lock();
                    try { token = newToken; }
                    finally { lock.writeLock().unlock(); }
                    log.info("Authenticated as {}", config.getEmail());
                    return;
                }
                log.error("Login rejected: {}", resp.getStatusCode());
            } catch (Exception ex) {
                log.error("Login failed ({}), retrying in 10 s…", ex.getMessage());
            }
            sleep(10_000);
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}