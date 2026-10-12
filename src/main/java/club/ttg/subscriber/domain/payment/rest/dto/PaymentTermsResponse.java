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
 * @param exactDiscounts скидки за точный срок (пасхалки), важнее ступеней; сайт их не рекламирует
 */
public record PaymentTermsResponse(
        BigDecimal monthPrice,
        String currency,
        Integer maxMonths,
        List<Discount> discounts,
        List<ExactDiscount> exactDiscounts
) {
    /**
     * Ступень скидки.
     *
     * @param fromMonths с какого срока действует
     * @param percent    скидка в процентах
     */
    public record Discount(int fromMonths, int percent) {
    }

    /**
     * Скидка за точный срок.
     *
     * @param months  срок, за который действует
     * @param percent скидка в процентах
     */
    public record ExactDiscount(int months, int percent) {
    }
}
