package club.ttg.subscriber.domain.payment.rest.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Условия оплаты подписки для окна покупки: сайт показывает цену из этих настроек,
 * а не из своего кода, так что сумма на экране совпадает с суммой платежа.
 *
 * @param monthPrice цена одного месяца
 * @param currency   валюта платежа
 * @param maxMonths  сколько месяцев можно оплатить за раз; {@code null} — без ограничения
 * @param discounts  ступени скидки за срок, по возрастанию срока
 */
public record PaymentTermsResponse(
        BigDecimal monthPrice,
        String currency,
        Integer maxMonths,
        List<Discount> discounts
) {
    /**
     * Ступень скидки.
     *
     * @param fromMonths с какого срока действует
     * @param percent    скидка в процентах
     */
    public record Discount(int fromMonths, int percent) {
    }
}
