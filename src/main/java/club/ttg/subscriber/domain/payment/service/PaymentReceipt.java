package club.ttg.subscriber.domain.payment.service;

import java.math.BigDecimal;

/**
 * Данные чека для ответа на уведомление об оплате: PayAnyWay берёт из них почту
 * покупателя ({@code CUSTOMER}) и позицию чека ({@code INVENTORY}).
 *
 * @param email  почта покупателя, куда придёт чек; {@code null} — не передаётся
 * @param item   название позиции в чеке
 * @param amount сумма позиции
 */
public record PaymentReceipt(
        String email,
        String item,
        BigDecimal amount
) {
}
