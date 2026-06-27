package club.ttg.subscriber.security;

import club.ttg.subscriber.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Утилита для работы с аутентифицированным пользователем, расшифрованным из JWT.
 */
public final class SecurityUtils {
    private SecurityUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Возвращает данные пользователя, расшифрованные из токена.
     *
     * @return данные пользователя
     */
    public static AuthUser getUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (Objects.isNull(authentication) || !(authentication.getPrincipal() instanceof AuthUser user)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Пользователь не авторизован");
            }

            return user;
        } catch (InsufficientAuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Пользователь не авторизован");
        }
    }

    /**
     * Возвращает стрим ролей пользователя, расшифрованных из токена.
     *
     * @return роли пользователя
     */
    public static Stream<String> userRoles() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .stream()
                .flatMap(a -> a.getAuthorities().stream())
                .map(GrantedAuthority::getAuthority);
    }

    /**
     * Возвращает логин текущего пользователя из токена.
     *
     * @return логин пользователя
     */
    public static String currentUsername() {
        return getUser().getUsername();
    }
}
