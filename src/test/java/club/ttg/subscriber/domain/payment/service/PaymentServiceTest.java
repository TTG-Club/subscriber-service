package club.ttg.subscriber.domain.payment.service;

import club.ttg.subscriber.domain.payment.model.Payment;
import club.ttg.subscriber.domain.payment.model.PaymentStatus;
import club.ttg.subscriber.domain.payment.repository.PaymentRepository;
import club.ttg.subscriber.domain.payment.rest.dto.CreatePaymentResponse;
import club.ttg.subscriber.domain.subscription.model.SubscriptionType;
import club.ttg.subscriber.domain.subscription.rest.dto.SubscriptionResponse;
import club.ttg.subscriber.domain.subscription.service.SubscriptionService;
import club.ttg.subscriber.exception.ApiException;
import club.ttg.subscriber.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    private static final String ACCOUNT = "12345678";
    private static final String CODE = "secret";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private PaymentService service(boolean testMode, boolean adminOnly) {
        PayAnyWaySettings settings = new PayAnyWaySettings(ACCOUNT, CODE,
                "https://www.payanyway.ru/assistant.htm", testMode, adminOnly, new BigDecimal("300"), 12,
                "https://dev.ttg.club/");
        return new PaymentService(paymentRepository, subscriptionService, settings);
    }

    private static void login(String... roles) {
        AuthUser user = new AuthUser(UUID.randomUUID(), "hero", "hero@example.com", List.of(roles));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private static Payment payment(UUID id, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setUuid(id);
        payment.setUsername("hero");
        payment.setAmount(new BigDecimal("300.00"));
        payment.setCurrency("RUB");
        payment.setMonths(1);
        payment.setStatus(status);
        payment.setTestMode(true);
        return payment;
    }

    private static Map<String, String> notification(UUID id, String amount, String testMode) {
        Map<String, String> params = new HashMap<>();
        params.put("MNT_ID", ACCOUNT);
        params.put("MNT_TRANSACTION_ID", id.toString());
        params.put("MNT_OPERATION_ID", "987654");
        params.put("MNT_AMOUNT", amount);
        params.put("MNT_CURRENCY_CODE", "RUB");
        params.put("MNT_TEST_MODE", testMode);
        params.put("MNT_SIGNATURE", PayAnyWaySignature.notification(ACCOUNT, id.toString(), "987654", amount,
                "RUB", null, testMode, CODE));
        return params;
    }

    private void stubPurchase() {
        when(subscriptionService.purchase(anyString(), anyInt())).thenReturn(new SubscriptionResponse(
                UUID.randomUUID(), SubscriptionType.BUY, "ACTIVE", 1, "hero", Instant.now(), Instant.now(),
                Instant.now(), Instant.now(), Instant.now()));
    }

    @Test
    void createRejectsNonAdminWhileAdminOnly() {
        login("USER");

        assertThatThrownBy(() -> service(true, true).create(1)).isInstanceOf(ApiException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createBuildsSignedFormUrl() {
        login("USER", "ADMIN");
        UUID id = UUID.randomUUID();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setUuid(id);
            return saved;
        });

        CreatePaymentResponse response = service(true, true).create(3);

        String signature = PayAnyWaySignature.request(ACCOUNT, id.toString(), "900.00", "RUB", null, "1", CODE);
        assertThat(response.testMode()).isTrue();
        assertThat(response.paymentUrl())
                .startsWith("https://www.payanyway.ru/assistant.htm?MNT_ID=" + ACCOUNT)
                .contains("MNT_TRANSACTION_ID=" + id)
                .contains("MNT_AMOUNT=900.00")
                .contains("MNT_TEST_MODE=1")
                .contains("MNT_SIGNATURE=" + signature)
                .contains("MNT_SUCCESS_URL=https://dev.ttg.club/vttg/subscription?payment%3Dsuccess")
                .doesNotContain(CODE + "&");
    }

    @Test
    void createRejectsTooManyMonths() {
        login("USER", "ADMIN");

        assertThatThrownBy(() -> service(true, true).create(13)).isInstanceOf(ApiException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paidNotificationExtendsSubscriptionOnce() {
        UUID id = UUID.randomUUID();
        Payment payment = payment(id, PaymentStatus.CREATED);
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.of(payment));
        stubPurchase();
        PaymentService service = service(true, true);

        PaymentNotificationResult first = service.handleNotification(notification(id, "300.00", "1"));
        PaymentNotificationResult repeated = service.handleNotification(notification(id, "300.00", "1"));

        assertThat(first.resultCode()).isEqualTo(PaymentNotificationResult.PAID);
        assertThat(repeated.resultCode()).isEqualTo(PaymentNotificationResult.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getOperationId()).isEqualTo("987654");
        verify(subscriptionService).purchase("hero", 1);
    }

    @Test
    void wrongSignatureAsksForRetry() {
        UUID id = UUID.randomUUID();
        Map<String, String> params = notification(id, "300.00", "1");
        params.put("MNT_SIGNATURE", "00000000000000000000000000000000");

        assertThat(service(true, true).handleNotification(params).isFail()).isTrue();
        verify(subscriptionService, never()).purchase(anyString(), anyInt());
    }

    @Test
    void wrongAmountIsRejected() {
        UUID id = UUID.randomUUID();
        Payment payment = payment(id, PaymentStatus.CREATED);
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.of(payment));

        PaymentNotificationResult result = service(true, true).handleNotification(notification(id, "1.00", "1"));

        assertThat(result.resultCode()).isEqualTo(PaymentNotificationResult.INVALID_ORDER);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        verify(subscriptionService, never()).purchase(anyString(), anyInt());
    }

    @Test
    void testPaymentIsRejectedInLiveMode() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.of(payment(id, PaymentStatus.CREATED)));

        PaymentNotificationResult result = service(false, false).handleNotification(notification(id, "300.00", "1"));

        assertThat(result.resultCode()).isEqualTo(PaymentNotificationResult.INVALID_ORDER);
        verify(subscriptionService, never()).purchase(anyString(), anyInt());
    }

    @Test
    void unknownPaymentIsRejected() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.empty());

        assertThat(service(true, true).handleNotification(notification(id, "300.00", "1")).resultCode())
                .isEqualTo(PaymentNotificationResult.INVALID_ORDER);
    }
}
