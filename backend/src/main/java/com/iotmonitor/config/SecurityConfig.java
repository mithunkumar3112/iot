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
                    "/performance.html",
                    "/screen.html",
                    "/history.html",
                    "/controls.html",
                    "/files.html",
                    "/pair.html",
                    "/onedrive.html",
                    "/style.css",
                    "/app.js",
                    "/manifest.json",
                    "/service-worker.js",
                    "/auth/**",
                    "/screenshot",
                    "/onedrive/**",
                    "/reverselink/discover",
                    "/reverselink/**"
            ).permitAll()

            // Allow file viewing/downloading
            .requestMatchers(
                    "/files/view/**",
                    "/files/read/**",
                    "/files/download/**",
                    "/files/list"
            ).permitAll()

            // Secure API endpoints
            .requestMatchers(
                    "/metrics/**",
                    "/commands/**"
            ).authenticated()

            .anyRequest().authenticated()
        )

        .addFilterBefore(jwtFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}