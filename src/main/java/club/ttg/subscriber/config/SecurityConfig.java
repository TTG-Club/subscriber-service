package club.ttg.subscriber.config;

import club.ttg.subscriber.security.InternalServiceTokenFilter;
import club.ttg.subscriber.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Конфигурация безопасности: stateless-цепочка фильтров без сессий и CSRF, авторизация
 * по ролям через {@code @Secured} ({@code securedEnabled = true}). Аутентификация
 * пользователей идёт {@link JwtAuthFilter}, а service-to-service маршруты
 * {@code /api/internal/**} закрыты {@link InternalServiceTokenFilter}; публично открыты
 * только actuator-health и swagger.
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final InternalServiceTokenFilter internalServiceTokenFilter;

    /**
     * CORS для фронта: разрешённые origin собираются из {@code app.frontend-base-url} и
     * {@code app.allowed-frontend-origins}. Дополнительно same-origin запрос (с учётом
     * X-Forwarded-Proto/Host за прокси) разрешается всегда, даже если его origin не в списке.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            @Value("${app.allowed-frontend-origins:}") String allowedFrontendOrigins
    ) {
        List<String> allowedOrigins = Arrays.stream((allowedFrontendOrigins + "," + frontendBaseUrl).split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return request -> {
            CorsConfiguration requestConfiguration = source.getCorsConfiguration(request);
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (requestConfiguration == null || origin == null || !isSameOrigin(origin, request)) {
                return requestConfiguration;
            }

            CorsConfiguration sameOriginConfiguration = new CorsConfiguration(requestConfiguration);
            List<String> requestAllowedOrigins = new ArrayList<>(Optional
                    .ofNullable(sameOriginConfiguration.getAllowedOrigins())
                    .orElse(List.of()));
            if (!requestAllowedOrigins.contains(origin)) {
                requestAllowedOrigins.add(origin);
            }
            sameOriginConfiguration.setAllowedOrigins(requestAllowedOrigins);
            return sameOriginConfiguration;
        };
    }

    private boolean isSameOrigin(String origin, HttpServletRequest request) {
        URI originUri = parseUri(origin);
        URI requestUri = parseUri(requestOrigin(request));
        if (originUri == null || requestUri == null) {
            return false;
        }

        return normalizeScheme(originUri).equals(normalizeScheme(requestUri))
                && normalizeHost(originUri).equals(normalizeHost(requestUri))
                && normalizePort(originUri) == normalizePort(requestUri);
    }

    private String requestOrigin(HttpServletRequest request) {
        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }

        String host = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        if (host == null || host.isBlank()) {
            host = request.getHeader(HttpHeaders.HOST);
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }

        return scheme + "://" + host;
    }

    private String firstHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        return value.split(",", 2)[0].trim();
    }

    private URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeScheme(URI uri) {
        return Optional.ofNullable(uri.getScheme())
                .orElse("")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeHost(URI uri) {
        return Optional.ofNullable(uri.getHost())
                .orElse("")
                .toLowerCase(Locale.ROOT);
    }

    private int normalizePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return switch (normalizeScheme(uri)) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    /**
     * Главная цепочка фильтров: stateless, без CSRF, с CORS; неаутентифицированный
     * запрос получает 401. Оба кастомных фильтра ставятся перед
     * {@link UsernamePasswordAuthenticationFilter}.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                ))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/error")
                        .permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        // /api/internal/** проходят на уровне Spring Security, но закрыты
                        // отдельным фильтром по заголовку X-Service-Token.
                        .requestMatchers("/api/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalServiceTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
