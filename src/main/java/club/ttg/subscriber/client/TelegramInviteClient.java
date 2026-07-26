package club.ttg.subscriber.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Выпуск одноразовых ссылок-приглашений в секретный чат разработки напрямую через
 * Telegram Bot API ({@code createChatInviteLink}, {@code member_limit=1}: после первого
 * входа Telegram гасит ссылку сам). Бот должен быть администратором чата с правом
 * приглашать пользователей.
 * <p>
 * Токен бота и id чата задаются переменными окружения {@code TELEGRAM_BOT_TOKEN} и
 * {@code TELEGRAM_DEV_CHAT_ID}; пока они не заданы, интеграция считается выключенной.
 */
@Slf4j
@Component
public class TelegramInviteClient {
    /** Лимит Telegram на длину подписи ссылки-приглашения. */
    private static final int INVITE_NAME_MAX_LENGTH = 32;

    private final RestClient restClient;
    private final String botToken;
    private final String devChatId;

    public TelegramInviteClient(
            RestClient.Builder restClientBuilder,
            @Value("${telegram.api-url}") String apiUrl,
            @Value("${telegram.bot-token}") String botToken,
            @Value("${telegram.dev-chat-id}") String devChatId,
            @Value("${telegram.connect-timeout:3s}") Duration connectTimeout,
            @Value("${telegram.read-timeout:10s}") Duration readTimeout
    ) {
        this.restClient = restClientBuilder
                .baseUrl(apiUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
        this.botToken = botToken;
        this.devChatId = devChatId;
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return factory;
    }

    /**
     * Выпускает одноразовую ссылку-приглашение в чат разработки.
     * Бросает исключение при ошибке транспорта/ответа — маппинг на ApiException
     * делает вызывающий код. ВАЖНО: сообщения {@code RestClientException} наружу
     * не переупаковываем и не логируем целиком — в них URL с токеном бота.
     *
     * @param name подпись ссылки в списке приглашений чата (обычно сам промокод,
     *             обрезается до 32 символов)
     * @return ссылка вида {@code https://t.me/+...}
     */
    public String createDevChatInvite(String name) {
        if (!StringUtils.hasText(botToken) || !StringUtils.hasText(devChatId)) {
            throw new IllegalStateException(
                    "Интеграция с Telegram не настроена: не заданы TELEGRAM_BOT_TOKEN или TELEGRAM_DEV_CHAT_ID");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", devChatId);
        payload.put("name", truncateName(name));
        payload.put("member_limit", 1);

        CreateInviteLinkResponse response = restClient.post()
                // Токен — часть пути (…/bot<token>/method). В шаблон не оборачиваем,
                // чтобы ':' в токене не был percent-энкоднут.
                .uri("/bot" + botToken + "/createChatInviteLink")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(CreateInviteLinkResponse.class);

        String inviteLink = response == null || response.result() == null
                ? null
                : response.result().inviteLink();
        if (!StringUtils.hasText(inviteLink)) {
            throw new IllegalStateException("Telegram вернул ответ без invite_link");
        }

        return inviteLink;
    }

    /**
     * Ответ Bot API {@code {"ok":true,"result":{"invite_link":"..."}}}. Достаём только
     * ссылку, остальные поля игнорируются. Осознанно record, а не JsonNode: приложение
     * живёт на Jackson 3 (Spring Boot 4), и его конвертер не поддерживает типы Jackson 2.
     *
     * @param ok     флаг успеха от Telegram
     * @param result объект созданной ссылки
     */
    public record CreateInviteLinkResponse(
            @JsonProperty("ok") Boolean ok,
            @JsonProperty("result") Result result
    ) {
        /**
         * Созданная ссылка-приглашение.
         *
         * @param inviteLink ссылка вида {@code https://t.me/+...}
         */
        public record Result(@JsonProperty("invite_link") String inviteLink) {
        }
    }

    private static String truncateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() <= INVITE_NAME_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, INVITE_NAME_MAX_LENGTH);
    }
}
