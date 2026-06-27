package club.ttg.subscriber.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Аутентификация по Bearer-токену auth-service. Принципал {@link AuthUser} собирается
 * прямо из claims (sub/username/email/roles), таблицы пользователей в этом сервисе нет.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String HEADER_NAME = "Authorization";

    private final JwtUtils jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HEADER_NAME);

        if (isInvalidAuthHeader(authHeader)) {
            filterChain.doFilter(request, response);

            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);

            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            if (Boolean.TRUE.equals(jwtService.isTokenValid(jwt))) {
                String username = jwtService.extractUsername(jwt);

                if (StringUtils.hasText(username)) {
                    authenticateUser(jwt, username, request);
                }
            }
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInvalidAuthHeader(String authHeader) {
        return authHeader == null || !authHeader.startsWith(BEARER_PREFIX);
    }

    private void authenticateUser(String jwt, String username, HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        AuthUser user = buildUser(jwt, username);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getAuthorities()
        );

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
    }

    private AuthUser buildUser(String jwt, String username) {
        UUID id = null;
        String userId = jwtService.extractUserId(jwt);

        if (StringUtils.hasText(userId)) {
            try {
                id = UUID.fromString(userId);
            } catch (IllegalArgumentException ignored) {
                // Старые core-api токены использовали username как subject — id остаётся null.
            }
        }

        return new AuthUser(id, username, jwtService.extractEmail(jwt), jwtService.extractRoles(jwt));
    }
}
