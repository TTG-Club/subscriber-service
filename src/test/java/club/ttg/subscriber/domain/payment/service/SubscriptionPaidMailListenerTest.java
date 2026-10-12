package club.ttg.subscriber.domain.payment.service;

import club.ttg.subscriber.client.AuthServiceMailClient;
import club.ttg.subscriber.client.AuthServiceMailClient.SubscriptionPaidMail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SubscriptionPaidMailListenerTest {
    private final AuthServiceMailClient mailClient = mock(AuthServiceMailClient.class);
    private final SubscriptionPaidMailListener listener = new SubscriptionPaidMailListener(mailClient);
    private final SubscriptionPaidMail mail = new SubscriptionPaidMail("buyer@example.com", "hero", 1,
            new BigDecimal("11.00"), Instant.now(), null);

    @Test
    void sendsMail() {
        listener.onSubscriptionPaid(mail);

        verify(mailClient).sendSubscriptionPaid(mail);
    }

    @Test
    void mailFailureDoesNotBreakPayment() {
        doThrow(new IllegalStateException("smtp down")).when(mailClient).sendSubscriptionPaid(mail);

        assertThatCode(() -> listener.onSubscriptionPaid(mail)).doesNotThrowAnyException();
    }
}
