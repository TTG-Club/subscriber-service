package club.ttg.subscriber.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Клиент чтения аккаунта текущего пользователя из auth-service: подтверждена ли
 * почта и даны ли согласия с офертой и на обработку персональных данных. Запрос
 * {@code GET {auth-service}/api/auth/me} идёт с токеном самого пользователя, поэтому
 * ответ всегда свежий, а не из клеймов токена.
 */
@Component
public class AuthServiceAccountClient {
    private static final String ME_PATH = "/api/auth/me";

    private final RestClient restClient;

    public AuthServiceAccountClient(
            RestClient.Builder restClientBuilder,
            @Value("${auth-service.base-url}") String authServiceBaseUrl,
            @Value("${auth-service.connect-timeout:2s}") Duration connectTimeout,
            @Value("${auth-service.read-timeout:3s}") Duration readTimeout
    ) {
        this.restClient = restClientBuilder
                .baseUrl(authServiceBaseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
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
     * Аккаунт пользователя, которому принадлежит заголовок авторизации. Бросает
     * исключение при ошибке транспорта или ответа — вызывающий код решает, что делать.
     *
     * @param authorization заголовок {@code Authorization} запроса пользователя
     */
    public AccountStatus currentAccount(String authorization) {
        AccountStatus account = restClient.get()
                .uri(ME_PATH)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(AccountStatus.class);

        return account == null ? new AccountStatus(false, false) : account;
    }

    /**
     * Нужная для оплаты часть ответа {@code /api/auth/me}.
     *
     * @param emailVerified    почта подтверждена
     * @param consentsAccepted приняты оферта и согласие на обработку персональных данных
     */
    public record AccountStatus(
            boolean emailVerified,
            boolean consentsAccepted
    ) {
    }
}
