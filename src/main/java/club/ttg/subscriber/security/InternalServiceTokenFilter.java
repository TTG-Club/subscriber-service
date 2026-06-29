package club.ttg.subscriber.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Защищает service-to-service маршруты `/api/internal/**` секретным заголовком
 * `X-Service-Token`. Совпадение с `internal.service-secret` обязательно: при его
 * отсутствии запрос отклоняется с 401, не доходя до контроллера. На остальных путях
 * фильтр прозрачен.
 */
@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Service-Token";
    private static final String INTERNAL_PREFIX = "/api/internal/";

    /** Виртуальная роль для внутренних вызовов (не пересекается с ролями пользователей). */
    private static final String INTERNAL_AUTHORITY = "INTERNAL_SERVICE";

    private final String serviceSecret;

    public InternalServiceTokenFilter(@Value("${internal.service-secret}") String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);

        if (!StringUtils.hasText(serviceSecret) || !serviceSecret.equals(provided)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "internal-service",
                null,
                List.of(new SimpleGrantedAuthority(INTERNAL_AUTHORITY))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
