package club.ttg.subscriber.domain.payment.service;

import club.ttg.subscriber.client.AuthServiceMailClient;
import club.ttg.subscriber.client.AuthServiceMailClient.SubscriptionPaidMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Отправляет покупателю наше письмо об оплате — только после того, как оплата и
 * продление подписки зафиксированы в базе. Письмо best-effort: ошибка почты не
 * отменяет оплату и не меняет ответ платёжному сервису.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionPaidMailListener {
    private final AuthServiceMailClient authServiceMailClient;

    @TransactionalEventListener
    public void onSubscriptionPaid(SubscriptionPaidMail mail) {
        try {
            authServiceMailClient.sendSubscriptionPaid(mail);
        } catch (RuntimeException exception) {
            log.error("Не удалось отправить письмо об оплате подписки {}: {}", mail.username(), exception.getMessage());
        }
    }
}
