package club.ttg.subscriber.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Клиент для начисления достижений в core-api (каталог достижений живёт там).
 * <p>
 * Сервис подписок хранит только коды достижений на промо-коде и при погашении делает
 * **best-effort** вызов `POST {core-api}/api/internal/achievements/grant`, защищённый
 * service-токеном `X-Service-Token`. Эндпоинт на стороне core-api появится позже —
 * пока вызов реализован, но его ошибки не должны валить погашение (см. вызов в сервисе).
 */
@Slf4j
@Component
public class CoreApiAchievementClient {
    private static final String GRANT_PATH = "/api/internal/achievements/grant";

    private final RestClient restClient;
    private final String serviceSecret;

    public CoreApiAchievementClient(
            RestClient.Builder restClientBuilder,
            @Value("${core-api.base-url}") String coreApiBaseUrl,
            @Value("${internal.service-secret}") String serviceSecret
    ) {
        this.restClient = restClientBuilder
                .baseUrl(coreApiBaseUrl)
                .requestFactory(requestFactory())
                .build();
        this.serviceSecret = serviceSecret;
    }

    /**
     * Фабрика с конечными таймаутами: best-effort вызов не должен блокировать поток
     * погашения, если core-api недоступен или отвечает медленно.
     */
    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        return factory;
    }

    /**
     * Просит core-api начислить пользователю достижения. Бросает исключение при ошибке
     * транспорта/ответа — вызывающий код оборачивает в try/catch (best-effort).
     *
     * @param username     логин пользователя
     * @param achievements коды достижений
     * @param sourceCode   код-источник (для аудита), может быть null
     */
    public void grant(String username, Set<String> achievements, UUID sourceCode) {
        if (achievements == null || achievements.isEmpty()) {
            return;
        }

        restClient.post()
                .uri(GRANT_PATH)
                .header("X-Service-Token", serviceSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GrantAchievementsRequest(username, achievements, sourceCode))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Тело запроса начисления достижений в core-api.
     *
     * @param username     логин пользователя
     * @param achievements коды достижений
     * @param sourceCode   код-источник (для аудита), может быть null
     */
    public record GrantAchievementsRequest(
            String username,
            Set<String> achievements,
            UUID sourceCode
    ) {
    }
}
