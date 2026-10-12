package club.ttg.subscriber.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Клиент писем через почту auth-service: subscriber сам SMTP не настраивает, а просит
 * auth-service отправить письмо внутренним эндпоинтом
 * {@code POST {auth-service}/api/internal/mail/subscription-paid}, защищённым
 * service-токеном {@code X-Service-Token}.
 */
@Component
public class AuthServiceMailClient {
    private static final String SUBSCRIPTION_PAID_PATH = "/api/internal/mail/subscription-paid";

    private final RestClient restClient;
    private final String serviceSecret;

    public AuthServiceMailClient(
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
     * подвешивать оплату дольше заданных таймаутов
     * ({@code auth-service.connect-timeout}/{@code auth-service.read-timeout}).
     */
    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return factory;
    }

    /**
     * Просит auth-service отправить письмо об оплате подписки. Бросает исключение при
     * ошибке транспорта или ответа — вызывающий код решает, что с ней делать.
     */
    public void sendSubscriptionPaid(SubscriptionPaidMail mail) {
        restClient.post()
                .uri(SUBSCRIPTION_PAID_PATH)
                .header("X-Service-Token", serviceSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mail)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Тело запроса письма об оплате (см. auth-service {@code InternalMailController}).
     *
     * @param email           почта, которую покупатель указал при оплате
     * @param username        логин покупателя
     * @param months          оплаченный срок в месяцах
     * @param amount          сумма оплаты
     * @param expiresAt       до какого момента теперь действует подписка
     * @param subscriptionUrl ссылка на страницу подписки; может быть null
     */
    public record SubscriptionPaidMail(
            String email,
            String username,
            Integer months,
            BigDecimal amount,
            Instant expiresAt,
            String subscriptionUrl
    ) {
    }
}
