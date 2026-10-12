package club.ttg.subscriber.domain.payment.rest.dto;

import java.math.BigDecimal;

/**
 * Условия оплаты подписки для окна покупки: сайт показывает цену из этих настроек,
 * а не из своего кода, так что сумма на экране совпадает с суммой платежа.
 *
 * @param monthPrice цена одного месяца
 * @param currency   валюта платежа
 * @param maxMonths  сколько месяцев можно оплатить за раз; {@code null} — без ограничения
 */
public record PaymentTermsResponse(
        BigDecimal monthPrice,
        String currency,
        Integer maxMonths
) {
}
