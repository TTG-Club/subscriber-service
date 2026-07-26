package club.ttg.subscriber.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Клиент выпуска одноразовых ссылок-приглашений в секретный Telegram-чат разработки.
 * Токен бота живёт только в core-api, поэтому сервис подписок просит ссылку через
 * межсервисную ручку `POST {core-api}/api/internal/telegram/dev-chat-invite`,
 * защищённую service-токеном `X-Service-Token`.
 * <p>
 * Вызов при выпуске кодов обязателен (ошибка откатывает выпуск), при погашении —
 * best-effort фолбэк для кодов, выпущенных до фичи (см. вызовы в сервисе).
 */
@Slf4j
@Component
public class CoreApiTelegramInviteClient {
    private static final String INVITE_PATH = "/api/internal/telegram/dev-chat-invite";

    private final RestClient restClient;
    private final String serviceSecret;

    public CoreApiTelegramInviteClient(
            RestClient.Builder restClientBuilder,
            @Value("${core-api.base-url}") String coreApiBaseUrl,
            @Value("${core-api.connect-timeout:2s}") Duration connectTimeout,
            @Value("${core-api.invite-read-timeout:15s}") Duration readTimeout,
            @Value("${internal.service-secret}") String serviceSecret
    ) {
        this.restClient = restClientBuilder
                .baseUrl(coreApiBaseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
        this.serviceSecret = serviceSecret;
    }

    /**
     * Фабрика с конечными таймаутами. Read-timeout здесь больше общего
     * {@code core-api.read-timeout:3s}: core-api внутри сам ходит в Telegram
     * (его read-timeout — 10s), и обрезать его ответ раньше нельзя.
     */
    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return factory;
    }

    /**
     * Просит core-api выпустить одноразовую ссылку-приглашение в чат разработки.
     * Бросает исключение при ошибке транспорта/ответа — маппинг на ApiException
     * делает вызывающий код.
     *
     * @param name подпись ссылки в списке приглашений чата (обычно сам промокод)
     * @return ссылка вида {@code https://t.me/+...}
     */
    public String createDevChatInvite(String name) {
        InviteResponse response = restClient.post()
                .uri(INVITE_PATH)
                .header("X-Service-Token", serviceSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateInviteRequest(name))
                .retrieve()
                .body(InviteResponse.class);
        if (response == null || response.inviteLink() == null || response.inviteLink().isBlank()) {
            throw new IllegalStateException("core-api вернул ответ без ссылки-приглашения");
        }

        return response.inviteLink();
    }

    /**
     * Тело запроса выпуска ссылки-приглашения.
     *
     * @param name подпись ссылки в списке приглашений чата
     */
    public record CreateInviteRequest(String name) {
    }

    /**
     * Ответ core-api с выпущенной ссылкой.
     *
     * @param inviteLink одноразовая ссылка вида {@code https://t.me/+...}
     */
    public record InviteResponse(String inviteLink) {
    }
}
