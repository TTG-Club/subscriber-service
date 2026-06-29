package club.ttg.subscriber.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Валидирует и разбирает JWT, выпущенный auth-service. Подпись проверяется тем же
 * общим секретом `auth-service.jwt-secret` (HMAC). Сервис только валидирует токены,
 * сам их не выпускает.
 */
@Component
public class JwtUtils {
    @Value("${auth-service.jwt-secret}")
    private String secretKey;

    /**
     * Логин из токена. Берётся из claim {@code username}, а при его отсутствии — из
     * subject (совместимость со старыми токенами, где subject содержал логин).
     */
    public String extractUsername(String token) {
        String username = extractClaim(token, claims -> claims.get("username", String.class));

        return username != null ? username : extractClaim(token, Claims::getSubject);
    }

    /**
     * Идентификатор пользователя (subject токена). В старых токенах core-api здесь мог
     * быть логин, а не UUID — это учитывает {@link JwtAuthFilter}.
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Email из claim {@code email}; {@code null}, если его нет в токене. */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /** Роли из claim {@code roles}; пустой список, если claim отсутствует или не является списком строк. */
    public List<String> extractRoles(String token) {
        Object roles = extractAllClaims(token).get("roles");

        if (roles instanceof List<?> roleList) {
            return roleList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        return Collections.emptyList();
    }

    /** Дата истечения токена (claim {@code exp}); {@code null}, если claim не задан. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Извлекает произвольный claim через resolver, разобрав и проверив подпись токена.
     *
     * @param claimsResolver функция, вытаскивающая нужное значение из {@link Claims}
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Проверяет токен: корректная подпись и срок действия. Любая ошибка разбора
     * трактуется как невалидность (возвращает {@code false}, не пробрасывает).
     */
    public Boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);

            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Истёк ли токен. Fail-closed: токен без claim `exp` считаем невалидным
     * (нельзя бессрочный токен), а не «никогда не истекающим».
     */
    public Boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);

        return expiration == null || expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(this.getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
