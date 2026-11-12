package com.prisaly.config;

import com.prisaly.m1.infrastructure.security.EstudianteSessionFilter;
import com.prisaly.m1.infrastructure.security.ProvisionalBearerAuthenticationFilter;
import com.prisaly.m1.application.port.StudentAccessPort;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
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
import org.springframework.security.authorization.AuthorizationDecision;
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
    private final StudentAccessPort studentAccess;

    public HttpApiConfig(
            @Value("${prisaly.cors.allowed-origins}") String allowedOrigins,
            ProvisionalBearerAuthenticationFilter provisionalBearerAuthenticationFilter,
            ObjectProvider<EstudianteSessionFilter> estudianteSessionFilter,
            ObjectProvider<StudentAccessPort> studentAccess) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.provisionalBearerAuthenticationFilter = provisionalBearerAuthenticationFilter;
        this.estudianteSessionFilter = estudianteSessionFilter.getIfAvailable();
        this.studentAccess = studentAccess.getIfAvailable();
        if (this.estudianteSessionFilter != null) {
            for (String origin : this.allowedOrigins) {
                URI uri = URI.create(origin);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getQuery() != null || uri.getFragment() != null
                        || (uri.getPath() != null && !uri.getPath().isEmpty())) {
                    throw new IllegalArgumentException("Student CORS requires explicit HTTP origins");
                }
            }
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(provisionalBearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    if (estudianteSessionFilter != null) {
                        auth.requestMatchers("/v1/estudiantes/**").access((identity, context) -> {
                            if (!safeMethod(context.getRequest()) && !trustedOrigin(context.getRequest())) {
                                return new AuthorizationDecision(false);
                            }
                            try {
                                if (studentAccess == null || studentAccess.requireCurrentStudent() == null) {
                                    throw new org.springframework.security.authentication.InsufficientAuthenticationException("Student authentication required");
                                }
                                return new AuthorizationDecision(true);
                            } catch (com.prisaly.m1.api.IdentityDomainException denied) {
                                throw new org.springframework.security.authentication.InsufficientAuthenticationException("Student authentication required");
                            }
                        });
                        auth.requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/**")
                                .access((identity, context) -> new AuthorizationDecision(trustedOrigin(context.getRequest())));
                    }
                    auth
                        // The container's internal error dispatch must render the original failure
                        // (e.g. 500); securing it turned infrastructure errors into 401.
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/personal/autenticar").permitAll()
                        // DD-02 B (D-B4, DC-DD02-B4-B5-01): /activar and /autenticar (alternative A) are retired.
                        .requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/solicitar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/validar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/acceso-estudiante/cerrar-sesion").permitAll()
                        .anyRequest().authenticated();
                })
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // If-Match / ETag carry the M3 aggregate precondition (DC-M3-M1-RES-03). Origins stay the
        // configured ones; no wildcard origins or headers.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "If-Match"));
        // Retry-After accompanies HTTP 429 on personnel login (DM1-06).
        configuration.setExposedHeaders(List.of("ETag", "Retry-After"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (estudianteSessionFilter != null) {
            CorsConfiguration student = new CorsConfiguration(configuration);
            student.setAllowCredentials(true);
            source.registerCorsConfiguration("/v1/estudiantes/**", student);
            source.registerCorsConfiguration("/v1/acceso-estudiante/**", student);
        }
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static boolean safeMethod(HttpServletRequest request) {
        return List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    }

    /** Browser CSRF boundary for cookie operations. No identity comes from Origin. */
    private boolean trustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || "null".equals(origin)) return false;
        String requestOrigin = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if (!("http".equals(request.getScheme()) && port == 80)
                && !("https".equals(request.getScheme()) && port == 443)) requestOrigin += ":" + port;
        return allowedOrigins.contains(origin) || requestOrigin.equals(origin);
    }
}
