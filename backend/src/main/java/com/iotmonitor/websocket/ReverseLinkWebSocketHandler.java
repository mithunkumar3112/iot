package com.iotmonitor.websocket;

import com.iotmonitor.controller.ReverseLinkAuthController;
import com.iotmonitor.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ReverseLinkWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private WatchService watchService;

    public ReverseLinkWebSocketHandler() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            String userHome = System.getProperty("user.home");
            
            // Watch specific directories
            Path pics = Paths.get(userHome, "Pictures").normalize().toAbsolutePath();
            Path docs = Paths.get(userHome, "Documents").normalize().toAbsolutePath();
            Path downloads = Paths.get(userHome, "Downloads").normalize().toAbsolutePath();
            
            if (Files.exists(pics)) pics.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            if (Files.exists(docs)) docs.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            if (Files.exists(downloads)) downloads.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            
            // Start watcher thread
            Thread watcherThread = new Thread(() -> {
                while (true) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                            
                            Path filename = (Path) event.context();
                            Path dir = (Path) key.watchable();
                            Path fullPath = dir.resolve(filename);
                            
                            broadcastEvent("{\"event\": \"file_changed\", \"path\": \"" + fullPath.toString().replace("\\", "\\\\") + "\", \"type\": \"" + kind.name() + "\"}");
                        }
                        boolean valid = key.reset();
                        if (!valid) break;
                    } catch (InterruptedException | ClosedWatchServiceException e) {
                        break;
                    }
                }
            });
            watcherThread.setDaemon(true);
            watcherThread.start();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        boolean authenticated = false;

        // 1. Try JWT from query param: ws://host/reverselink/ws?token=<jwt>
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        String token = param.substring(6);
                        if (jwtTokenProvider != null && jwtTokenProvider.validateToken(token)) {
                            authenticated = true;
                        }
                        break;
                    }
                }
            }
        }

        // 2. Fall back to legacy ECDH pairing
        if (!authenticated && ReverseLinkAuthController.derivedAesKey != null) {
            authenticated = true;
        }

        if (!authenticated) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized - Connect via email or QR code."));
            return;
        }
        sessions.add(session);
        System.out.println("ReverseLink Android peer connected via WebSocket!");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("ReverseLink Android peer disconnected.");
    }

    private void broadcastEvent(String payload) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
