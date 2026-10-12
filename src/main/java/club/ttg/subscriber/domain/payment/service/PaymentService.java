package club.ttg.subscriber.domain.payment.service;

import club.ttg.subscriber.client.AuthServiceAccountClient;
import club.ttg.subscriber.client.AuthServiceAccountClient.AccountStatus;
import club.ttg.subscriber.client.AuthServiceMailClient.SubscriptionPaidMail;
import club.ttg.subscriber.domain.payment.model.Payment;
import club.ttg.subscriber.domain.payment.model.PaymentStatus;
import club.ttg.subscriber.domain.payment.repository.PaymentRepository;
import club.ttg.subscriber.domain.payment.rest.dto.CreatePaymentRequest;
import club.ttg.subscriber.domain.payment.rest.dto.CreatePaymentResponse;
import club.ttg.subscriber.domain.payment.rest.dto.PaymentTermsResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.SubscriptionResponse;
import club.ttg.subscriber.domain.subscription.service.SubscriptionService;
import club.ttg.subscriber.exception.ApiException;
import club.ttg.subscriber.security.AuthUser;
import club.ttg.subscriber.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Оплата подписки через PayAnyWay (MONETA.Assistant): создание платежа с подписанной
 * ссылкой на платёжную форму и обработка уведомления об оплате (Pay URL), после
 * которого подписка продлевается. Повторные уведомления по уже оплаченному платежу
 * подтверждаются без повторного продления.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final String CURRENCY = "RUB";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String TEST_MODE_ON = "1";
    private static final String TEST_MODE_OFF = "0";
    private static final String SUBSCRIPTION_PAGE_PATH = "/vttg/subscription";
    /** Форма сразу открывает экран оплаты картой; СБП доступна там же отдельной кнопкой. */
    private static final String CARD_PAYMENT_UNIT = "card";

    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final PayAnyWaySettings settings;
    private final AuthServiceAccountClient authServiceAccountClient;
    private final ApplicationEventPublisher eventPublisher;

    /** Текущие условия оплаты: цена месяца, валюта и предел срока из настроек. */
    public PaymentTermsResponse terms() {
        if (!settings.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Оплата временно недоступна");
        }
        return new PaymentTermsResponse(
                settings.getPrice().setScale(2, RoundingMode.HALF_UP),
                CURRENCY,
                settings.hasMaxMonths() ? settings.getMaxMonths() : null);
    }

    /**
     * Создаёт платёж текущего пользователя за выбранное число месяцев (цена месяца,
     * умноженная на срок) и возвращает ссылку на платёжную форму. Автопродления нет.
     * Платить может только аккаунт с подтверждённой почтой и принятыми офертой и
     * согласием на обработку персональных данных (проверяется в auth-service).
     * Пока включён {@code payanyway.admin-only}, платить могут только администраторы.
     *
     * @param request       срок и почта для чека
     * @param authorization заголовок авторизации пользователя — для запроса в auth-service
     */
    @Transactional
    public CreatePaymentResponse create(CreatePaymentRequest request, String authorization) {
        int months = request.months();
        AuthUser user = SecurityUtils.getUser();
        if (settings.isAdminOnly() && !user.getRoles().contains(ADMIN_ROLE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Оплата пока доступна только администраторам");
        }
        if (!settings.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Оплата временно недоступна");
        }
        if (months < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Срок оплаты должен быть не меньше 1 месяца");
        }
        if (settings.hasMaxMonths() && months > settings.getMaxMonths()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Оплатить можно не больше " + settings.getMaxMonths() + " мес. за раз");
        }
        requireAccountReady(authorization);

        Payment payment = new Payment();
        payment.setUsername(user.getUsername());
        payment.setEmail(request.email().trim());
        payment.setAmount(settings.getPrice().multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP));
        payment.setCurrency(CURRENCY);
        payment.setMonths(months);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setTestMode(settings.isTestMode());
        payment = paymentRepository.save(payment);

        return new CreatePaymentResponse(payment.getUuid(), paymentUrl(payment), payment.isTestMode());
    }

    /**
     * Пускает к оплате только аккаунт с подтверждённой почтой и принятыми согласиями.
     * Если auth-service не ответил, оплата не создаётся: проверить аккаунт нечем.
     */
    private void requireAccountReady(String authorization) {
        AccountStatus account;
        try {
            account = authServiceAccountClient.currentAccount(authorization);
        } catch (RuntimeException exception) {
            log.error("PayAnyWay: не удалось проверить аккаунт в auth-service: {}", exception.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Не удалось проверить аккаунт, попробуйте чуть позже");
        }

        if (!account.emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Подтвердите почту, чтобы оплатить подписку");
        }
        if (!account.consentsAccepted()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Примите условия оферты и дайте согласие на обработку персональных данных");
        }
    }

    /**
     * Обрабатывает уведомление об оплате. Неверная подпись или незаданные настройки —
     * {@code FAIL} (платёжный сервис повторит уведомление); платёж, который нельзя
     * принять (не найден, другая сумма, валюта или тестовая оплата в рабочем режиме), —
     * код 500; принятая оплата, в том числе повторная, — код 200.
     */
    @Transactional
    public PaymentNotificationResult handleNotification(Map<String, String> params) {
        if (!settings.isConfigured()) {
            log.error("PayAnyWay: уведомление пришло, но номер счёта, код проверки целостности или цена месяца не заданы");
            return PaymentNotificationResult.fail();
        }

        String accountId = params.get("MNT_ID");
        String transactionId = params.get("MNT_TRANSACTION_ID");
        String operationId = params.get("MNT_OPERATION_ID");
        String amount = params.get("MNT_AMOUNT");
        String currency = params.get("MNT_CURRENCY_CODE");
        String testMode = params.get("MNT_TEST_MODE");

        String expected = PayAnyWaySignature.notification(accountId, transactionId, operationId, amount,
                currency, params.get("MNT_SUBSCRIBER_ID"), testMode, settings.getIntegrityCode());
        if (!PayAnyWaySignature.matches(expected, params.get("MNT_SIGNATURE"))) {
            log.warn("PayAnyWay: неверная подпись уведомления, платёж {}", transactionId);
            return PaymentNotificationResult.fail();
        }

        if (!settings.getAccountId().equals(accountId)) {
            log.warn("PayAnyWay: уведомление для чужого счёта {}, платёж {}", accountId, transactionId);
            return invalid(accountId, transactionId);
        }

        Optional<Payment> found = parseUuid(transactionId).flatMap(paymentRepository::findForUpdate);
        if (found.isEmpty()) {
            log.warn("PayAnyWay: платёж {} не найден", transactionId);
            return invalid(accountId, transactionId);
        }

        Payment payment = found.get();
        if (payment.getStatus() == PaymentStatus.PAID) {
            return paid(accountId, transactionId, payment);
        }
        if (payment.getStatus() == PaymentStatus.REJECTED) {
            return invalid(accountId, transactionId);
        }

        String rejection = rejectionReason(payment, amount, currency, testMode);
        if (rejection != null) {
            log.warn("PayAnyWay: платёж {} отклонён: {}", transactionId, rejection);
            payment.setStatus(PaymentStatus.REJECTED);
            payment.setOperationId(operationId);
            paymentRepository.save(payment);
            return invalid(accountId, transactionId);
        }

        SubscriptionResponse subscription = subscriptionService.purchase(payment.getUsername(), payment.getMonths());
        payment.setStatus(PaymentStatus.PAID);
        payment.setOperationId(operationId);
        payment.setTestMode(TEST_MODE_ON.equals(testMode));
        payment.setPaidAt(Instant.now());
        payment.setSubscriptionUuid(subscription.id());
        paymentRepository.save(payment);
        publishPaidMail(payment, subscription);
        log.info("PayAnyWay: платёж {} оплачен (операция {}, тест: {}), подписка {} продлена до {}",
                transactionId, operationId, payment.isTestMode(), payment.getUsername(), subscription.expiresAt());

        return paid(accountId, transactionId, payment);
    }

    /** Письмо об оплате уйдёт после фиксации транзакции (см. {@link SubscriptionPaidMailListener}). */
    private void publishPaidMail(Payment payment, SubscriptionResponse subscription) {
        if (!StringUtils.hasText(payment.getEmail())) {
            return;
        }
        String subscriptionUrl = StringUtils.hasText(settings.getSiteUrl())
                ? settings.getSiteUrl() + SUBSCRIPTION_PAGE_PATH
                : null;
        eventPublisher.publishEvent(new SubscriptionPaidMail(payment.getEmail(), payment.getUsername(),
                payment.getMonths(), payment.getAmount(), subscription.expiresAt(), subscriptionUrl));
    }

    /** Подпись XML-ответа на уведомление. */
    public String responseSignature(PaymentNotificationResult result) {
        return PayAnyWaySignature.response(String.valueOf(result.resultCode()), result.accountId(),
                result.transactionId(), settings.getIntegrityCode());
    }

    private String rejectionReason(Payment payment, String amount, String currency, String testMode) {
        if (TEST_MODE_ON.equals(testMode) && !settings.isTestMode()) {
            return "тестовая оплата при выключенном тестовом режиме";
        }
        if (!payment.getCurrency().equals(currency)) {
            return "валюта " + currency;
        }
        BigDecimal paidAmount = parseAmount(amount);
        if (paidAmount == null || paidAmount.compareTo(payment.getAmount()) != 0) {
            return "сумма " + amount + " вместо " + payment.getAmount();
        }
        return null;
    }

    private String paymentUrl(Payment payment) {
        String accountId = settings.getAccountId();
        String transactionId = payment.getUuid().toString();
        String amount = payment.getAmount().toPlainString();
        String testMode = payment.isTestMode() ? TEST_MODE_ON : TEST_MODE_OFF;

        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(settings.getFormUrl())
                .queryParam("MNT_ID", accountId)
                .queryParam("MNT_TRANSACTION_ID", transactionId)
                .queryParam("MNT_CURRENCY_CODE", payment.getCurrency())
                .queryParam("MNT_AMOUNT", amount)
                .queryParam("MNT_TEST_MODE", testMode)
                .queryParam("MNT_DESCRIPTION", description(payment))
                .queryParam("MNT_SIGNATURE", PayAnyWaySignature.request(accountId, transactionId, amount,
                        payment.getCurrency(), null, testMode, settings.getIntegrityCode()))
                .queryParam("paymentSystem.unitId", CARD_PAYMENT_UNIT)
                .queryParam("moneta.locale", "ru");

        if (StringUtils.hasText(settings.getSiteUrl())) {
            String page = settings.getSiteUrl() + SUBSCRIPTION_PAGE_PATH;
            url.queryParam("MNT_SUCCESS_URL", page + "?payment=success")
                    .queryParam("MNT_INPROGRESS_URL", page + "?payment=pending")
                    .queryParam("MNT_FAIL_URL", page + "?payment=fail")
                    .queryParam("MNT_RETURN_URL", page + "?payment=cancel");
        }

        return url.encode(StandardCharsets.UTF_8).build().toUriString();
    }

    /** Название позиции в чеке: услуга подписки на оплаченный срок. */
    private static String receiptItem(Payment payment) {
        return "Подписка TTG Club на " + payment.getMonths() + " мес.";
    }

    private static String description(Payment payment) {
        return "Подписка TTG Club на " + payment.getMonths() + " мес. (" + payment.getUsername() + ")";
    }

    private static PaymentNotificationResult paid(String accountId, String transactionId, Payment payment) {
        PaymentReceipt receipt = new PaymentReceipt(payment.getEmail(), receiptItem(payment), payment.getAmount());
        return new PaymentNotificationResult(PaymentNotificationResult.PAID, accountId, transactionId, receipt);
    }

    private static PaymentNotificationResult invalid(String accountId, String transactionId) {
        return new PaymentNotificationResult(PaymentNotificationResult.INVALID_ORDER, accountId, transactionId, null);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private static BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
