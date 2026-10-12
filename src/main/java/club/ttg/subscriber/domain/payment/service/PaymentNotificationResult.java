package club.ttg.subscriber.domain.payment.service;

/**
 * Итог обработки уведомления об оплате (Pay URL).
 *
 * @param resultCode    {@code MNT_RESULT_CODE} для XML-ответа: 200 — оплата принята,
 *                      500 — заказ неактуален; {@code null} — ответить текстом {@code FAIL},
 *                      чтобы платёжный сервис повторил уведомление позже
 * @param accountId     {@code MNT_ID} из уведомления
 * @param transactionId {@code MNT_TRANSACTION_ID} из уведомления
 * @param receipt       данные чека для принятой оплаты; {@code null} — без чека
 */
public record PaymentNotificationResult(
        Integer resultCode,
        String accountId,
        String transactionId,
        PaymentReceipt receipt
) {
    public static final int PAID = 200;
    public static final int INVALID_ORDER = 500;

    /** Уведомление не обработано: платёжный сервис повторит его. */
    public static PaymentNotificationResult fail() {
        return new PaymentNotificationResult(null, null, null, null);
    }

    public boolean isFail() {
        return resultCode == null;
    }
}
