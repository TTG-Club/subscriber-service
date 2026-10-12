package club.ttg.subscriber.domain.payment.rest.controller;

import club.ttg.subscriber.domain.payment.service.PaymentNotificationResult;
import club.ttg.subscriber.domain.payment.service.PaymentReceipt;
import club.ttg.subscriber.domain.payment.service.PaymentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentControllerTest {
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentController controller = new PaymentController(paymentService);

    @Test
    void paidResponseCarriesReceiptForCustomer() {
        PaymentNotificationResult result = new PaymentNotificationResult(PaymentNotificationResult.PAID, "123", "tx",
                new PaymentReceipt("hero@example.com", "Подписка TTG Club на 2 мес.", new BigDecimal("22.00")));
        when(paymentService.handleNotification(any())).thenReturn(result);
        when(paymentService.responseSignature(result)).thenReturn("sig");

        String body = controller.notification(Map.of()).getBody();

        assertThat(body)
                .contains("<MNT_RESULT_CODE>200</MNT_RESULT_CODE><MNT_SIGNATURE>sig</MNT_SIGNATURE><MNT_ATTRIBUTES>")
                .contains("<ATTRIBUTE><KEY>CUSTOMER</KEY><VALUE>hero@example.com</VALUE></ATTRIBUTE>")
                .contains("<KEY>INVENTORY</KEY><VALUE>[{&quot;name&quot;:&quot;Подписка TTG Club на 2 мес.&quot;,"
                        + "&quot;price&quot;:22.00,&quot;quantity&quot;:1,&quot;vatTag&quot;:1105,"
                        + "&quot;pm&quot;:&quot;full_payment&quot;,&quot;po&quot;:&quot;service&quot;}]</VALUE>");
    }

    @Test
    void rejectedResponseHasNoReceipt() {
        PaymentNotificationResult result = new PaymentNotificationResult(
                PaymentNotificationResult.INVALID_ORDER, "123", "tx", null);
        when(paymentService.handleNotification(any())).thenReturn(result);
        when(paymentService.responseSignature(result)).thenReturn("sig");

        assertThat(controller.notification(Map.of()).getBody()).doesNotContain("MNT_ATTRIBUTES");
    }
}
