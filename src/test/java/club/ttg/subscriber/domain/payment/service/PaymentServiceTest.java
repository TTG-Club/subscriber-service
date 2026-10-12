package club.ttg.subscriber.domain.payment.service;

import club.ttg.subscriber.client.AuthServiceAccountClient;
import club.ttg.subscriber.client.AuthServiceAccountClient.AccountStatus;
import club.ttg.subscriber.client.AuthServiceMailClient.SubscriptionPaidMail;
import club.ttg.subscriber.domain.payment.model.Payment;
import club.ttg.subscriber.domain.payment.model.PaymentStatus;
import club.ttg.subscriber.domain.payment.repository.PaymentRepository;
import club.ttg.subscriber.domain.payment.rest.dto.CreatePaymentRequest;
import club.ttg.subscriber.domain.payment.rest.dto.CreatePaymentResponse;
import club.ttg.subscriber.domain.subscription.model.SubscriptionType;
import club.ttg.subscriber.domain.subscription.rest.dto.SubscriptionResponse;
import club.ttg.subscriber.domain.subscription.service.SubscriptionService;
import club.ttg.subscriber.exception.ApiException;
import club.ttg.subscriber.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    private static final String ACCOUNT = "12345678";
    private static final String CODE = "secret";
    private static final String TOKEN = "Bearer token";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final AuthServiceAccountClient accountClient = mock(AuthServiceAccountClient.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private PaymentService service(boolean testMode, boolean adminOnly) {
        return service(testMode, adminOnly, 0);
    }

    private PaymentService service(boolean testMode, boolean adminOnly, int maxMonths) {
        PayAnyWaySettings settings = new PayAnyWaySettings(ACCOUNT, CODE,
                "https://www.payanyway.ru/assistant.htm", testMode, adminOnly, new BigDecimal("300"), maxMonths,
                "https://dev.ttg.club/");
        return new PaymentService(paymentRepository, subscriptionService, settings, accountClient, eventPublisher);
    }

    private static CreatePaymentRequest request(int months) {
        return new CreatePaymentRequest(months, "receipt@example.com");
    }

    private void account(boolean emailVerified, boolean consentsAccepted) {
        when(accountClient.currentAccount(TOKEN)).thenReturn(new AccountStatus(emailVerified, consentsAccepted));
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
        when(subscriptionService.purchase(anyString(), anyInt(), any())).thenReturn(new SubscriptionResponse(
                UUID.randomUUID(), SubscriptionType.BUY, "ACTIVE", 1, "hero", Instant.now(), Instant.now(),
                Instant.now(), Instant.now(), Instant.now()));
    }

    @Test
    void termsExposeConfiguredPrice() {
        assertThat(service(true, true).terms().monthPrice()).isEqualByComparingTo("300.00");
        assertThat(service(true, true).terms().maxMonths()).isNull();
        assertThat(service(true, true, 12).terms().maxMonths()).isEqualTo(12);
    }

    @Test
    void paymentsDisabledWithoutPrice() {
        login("ADMIN");
        PaymentService noPrice = new PaymentService(paymentRepository, subscriptionService,
                new PayAnyWaySettings(ACCOUNT, CODE, "https://www.payanyway.ru/assistant.htm",
                        true, true, null, 0, "https://dev.ttg.club/"), accountClient, eventPublisher);

        assertThatThrownBy(noPrice::terms).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> noPrice.create(request(1), TOKEN)).isInstanceOf(ApiException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createRequiresVerifiedEmail() {
        login("USER", "ADMIN");
        account(false, true);

        assertThatThrownBy(() -> service(true, true).create(request(1), TOKEN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Подтвердите почту");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createRequiresConsents() {
        login("USER", "ADMIN");
        account(true, false);

        assertThatThrownBy(() -> service(true, true).create(request(1), TOKEN))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("оферты");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createFailsClosedWhenAuthServiceIsDown() {
        login("USER", "ADMIN");
        when(accountClient.currentAccount(TOKEN)).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service(true, true).create(request(1), TOKEN)).isInstanceOf(ApiException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createRejectsNonAdminWhileAdminOnly() {
        login("USER");

        assertThatThrownBy(() -> service(true, true).create(request(1), TOKEN)).isInstanceOf(ApiException.class);
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

        account(true, true);

        CreatePaymentResponse response = service(true, true).create(request(3), TOKEN);

        String signature = PayAnyWaySignature.request(ACCOUNT, id.toString(), "900.00", "RUB", null, "1", CODE);
        assertThat(response.testMode()).isTrue();
        assertThat(response.paymentUrl())
                .startsWith("https://www.payanyway.ru/assistant.htm?MNT_ID=" + ACCOUNT)
                .contains("MNT_TRANSACTION_ID=" + id)
                .contains("MNT_AMOUNT=900.00")
                .contains("MNT_TEST_MODE=1")
                .contains("MNT_SIGNATURE=" + signature)
                .contains("paymentSystem.unitId=card")
                .contains("MNT_SUCCESS_URL=https://dev.ttg.club/subscription?payment%3Dsuccess")
                .doesNotContain(CODE + "&");
        verify(paymentRepository).save(argThat(saved -> "receipt@example.com".equals(saved.getEmail())));
    }

    @Test
    void createRejectsTooManyMonths() {
        login("USER", "ADMIN");

        assertThatThrownBy(() -> service(true, true, 12).create(request(13), TOKEN)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service(true, true).create(request(0), TOKEN)).isInstanceOf(ApiException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paidNotificationExtendsSubscriptionOnce() {
        UUID id = UUID.randomUUID();
        Payment payment = payment(id, PaymentStatus.CREATED);
        payment.setEmail("hero@example.com");
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.of(payment));
        stubPurchase();
        PaymentService service = service(true, true);

        PaymentNotificationResult first = service.handleNotification(notification(id, "300.00", "1"));
        PaymentNotificationResult repeated = service.handleNotification(notification(id, "300.00", "1"));

        assertThat(first.resultCode()).isEqualTo(PaymentNotificationResult.PAID);
        assertThat(repeated.resultCode()).isEqualTo(PaymentNotificationResult.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getOperationId()).isEqualTo("987654");
        verify(subscriptionService).purchase(eq("hero"), eq(1), any());
        assertThat(first.receipt()).isEqualTo(
                new PaymentReceipt("hero@example.com", "Подписка TTG Club на 1 мес.", new BigDecimal("300.00")));
        assertThat(repeated.receipt()).isEqualTo(first.receipt());
        verify(eventPublisher).publishEvent(argThat((Object event) -> event instanceof SubscriptionPaidMail mail
                && "hero@example.com".equals(mail.email()) && mail.months() == 1
                && "https://dev.ttg.club/subscription".equals(mail.subscriptionUrl())));
    }

    @Test
    void wrongSignatureAsksForRetry() {
        UUID id = UUID.randomUUID();
        Map<String, String> params = notification(id, "300.00", "1");
        params.put("MNT_SIGNATURE", "00000000000000000000000000000000");

        assertThat(service(true, true).handleNotification(params).isFail()).isTrue();
        verify(subscriptionService, never()).purchase(anyString(), anyInt(), any());
    }

    @Test
    void wrongAmountIsRejected() {
        UUID id = UUID.randomUUID();
        Payment payment = payment(id, PaymentStatus.CREATED);
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.of(payment));

        PaymentNotificationResult result = service(true, true).handleNotification(notification(id, "1.00", "1"));

        assertThat(result.resultCode()).isEqualTo(PaymentNotificationResult.INVALID_ORDER);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        verify(subscriptionService, never()).purchase(anyString(), anyInt(), any());
    }

    @Test
    void testPaymentIsRejectedInLiveMode() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.of(payment(id, PaymentStatus.CREATED)));

        PaymentNotificationResult result = service(false, false).handleNotification(notification(id, "300.00", "1"));

        assertThat(result.resultCode()).isEqualTo(PaymentNotificationResult.INVALID_ORDER);
        verify(subscriptionService, never()).purchase(anyString(), anyInt(), any());
    }

    @Test
    void unknownPaymentIsRejected() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findForUpdate(id)).thenReturn(Optional.empty());

        assertThat(service(true, true).handleNotification(notification(id, "300.00", "1")).resultCode())
                .isEqualTo(PaymentNotificationResult.INVALID_ORDER);
    }
}
