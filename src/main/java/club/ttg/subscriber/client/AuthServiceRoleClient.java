package club.ttg.subscriber.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Клиент выдачи ролей в auth-service (роли живут там). При погашении кода с ранним
 * доступом subscriber просит auth-service выдать пользователю роль VTTG, вызывая
 * внутренний эндпоинт {@code POST {auth-service}/api/internal/roles/grant} (тело
 * {username, roleName}), защищённый service-токеном {@code X-Service-Token}.
 * <p>
 * В отличие от начисления достижений ({@link CoreApiAchievementClient}) вызов НЕ
 * best-effort: ошибка пробрасывается наружу, чтобы погашение откатилось целиком
 * (роль и код выдаются атомарно, без «часть выдалась — часть нет»).
 */
@Slf4j
@Component
public class AuthServiceRoleClient {
    private static final String GRANT_ROLE_PATH = "/api/internal/roles/grant";

    private final RestClient restClient;
    private final String serviceSecret;

    public AuthServiceRoleClient(
            RestClient.Builder restClientBuilder,
            @Value("${auth-service.base-url}") String authServiceBaseUrl,
            @Value("${auth-service.connect-timeout:2s}") Duration connectTimeout,
            @Value("${auth-service.read-timeout:3s}") Duration readTimeout,
            @Value("${internal.service-secret}") String serviceSecret
    ) {
        this.restClient = restClientBuilder
                .baseUrl(authServiceBaseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
        this.serviceSecret = serviceSecret;
    }

    /**
     * Фабрика с конечными таймаутами: медленный/недоступный auth-service не должен
     * подвешивать поток погашения дольше заданных таймаутов
     * ({@code auth-service.connect-timeout}/{@code auth-service.read-timeout}).
     */
    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return factory;
    }

    /**
     * Просит auth-service выдать пользователю роль. Идентификатор и имя роли передаются
     * телом (не в пути), чтобы спецсимволы в username не ломали запрос. Бросает исключение
     * при ошибке транспорта/ответа — вызывающий код НЕ глушит её (грант обязателен, см.
     * класс-doc). Выдача на стороне auth-service идемпотентна.
     *
     * @param username логин пользователя (идентификатор между сервисами)
     * @param roleName имя роли, напр. {@code "VTTG"}
     */
    public void grantRole(String username, String roleName) {
        restClient.post()
                .uri(GRANT_ROLE_PATH)
                .header("X-Service-Token", serviceSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GrantRoleRequest(username, roleName))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Тело запроса выдачи роли (см. auth-service {@code InternalRoleController}).
     *
     * @param username логин пользователя
     * @param roleName имя роли
     */
    public record GrantRoleRequest(
            String username,
            String roleName
    ) {
    }
}
