package com.iotmonitor.config;

import com.iotmonitor.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.Collections;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())

        .authorizeHttpRequests(auth -> auth

            // Public pages
            .requestMatchers(
                    "/",
                    "/login.html",
                    "/dashboard.html",
                    "/screen-monitor.html",
                    "/clipboard.html",
                    "/activity-timeline.html",
                    "/security-alerts.html",
                    "/session-history.html",
                    "/files-explorer.html",
                    "/processes.html",
                    "/pair.html",
                    "/connect.html", 
                    "/style.css",
                    "/dashboard-shell.css",
                    "/dashboard-shell.js",
                    "/app.js",
                    "/navigation.js",
                    "/manifest.json",
                    "/service-worker.js",
                    "/auth/**",
                    "/reverselink/discover",
                    "/reverselink/**",
                    "/favicon.ico",
                    "/upload",
                    "/upload/**",
                    "/upload/**",
                    "/upload-chunk",
                    "/api/android/**",
                    "/api/files/**",
                    "/api/screenshots/**",
                    "/api/processes/**",
                    "/api/alerts/**",
                    "/security/**",
                    "/sessions",
                    "/sessions/**",
                    "/metrics/**",
                    "/processes",
                    "/processes/**",
                    "/apps",
                    "/apps/**",
                    "/apps/activity",
                    "/system/battery",
                    "/system/battery/**",
                    "/clipboard",
                    "/clipboard/**",
                    "/activity",
                    "/activity/**",
                    "/ws",
                    "/ws/**"
            ).permitAll()

            // Allow file viewing/downloading
            .requestMatchers(
                    "/files/view/**",
                    "/files/read/**",
                    "/files/download/**",
                    "/files/list",
                    "/files/all",
                    "/files/recent",
                    "/files/device/**",
                    "/files/supabase"
            ).permitAll()

            // Secure API endpoints
            .requestMatchers(
                    "/commands/history",
                    "/commands/on",
                    "/commands/off",
                    "/commands/shutdown",
                    "/commands/sleep",
                    "/commands/restart-agent"
            ).authenticated()

            // Agent command polling/result endpoints are unauthenticated because the
            // laptop agent does not currently carry a JWT token.
            .requestMatchers(
                    "/commands/{deviceId}",
                    "/commands/send",
                    "/commands/result",
                    "/commands/latest"
            ).permitAll()

            .anyRequest().authenticated()
        )

        .addFilterBefore(jwtFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
