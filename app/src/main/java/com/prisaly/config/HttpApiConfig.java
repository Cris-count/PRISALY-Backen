package com.prisaly.config;

import com.prisaly.m1.infrastructure.security.EstudianteSessionFilter;
import com.prisaly.m1.infrastructure.security.ProvisionalBearerAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class HttpApiConfig {

    private final List<String> allowedOrigins;
    private final ProvisionalBearerAuthenticationFilter provisionalBearerAuthenticationFilter;
    private final EstudianteSessionFilter estudianteSessionFilter;

    public HttpApiConfig(
            @Value("${prisaly.cors.allowed-origins}") String allowedOrigins,
            ProvisionalBearerAuthenticationFilter provisionalBearerAuthenticationFilter,
            ObjectProvider<EstudianteSessionFilter> estudianteSessionFilter) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.provisionalBearerAuthenticationFilter = provisionalBearerAuthenticationFilter;
        this.estudianteSessionFilter = estudianteSessionFilter.getIfAvailable();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (estudianteSessionFilter != null) {
            http.addFilterBefore(estudianteSessionFilter, ProvisionalBearerAuthenticationFilter.class);
        }
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .anonymous(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        // The container's internal error dispatch must render the original failure
                        // (e.g. 500); securing it turned infrastructure errors into 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/personal/autenticar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/validar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/activar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/autenticar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/cerrar-sesion").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        provisionalBearerAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
