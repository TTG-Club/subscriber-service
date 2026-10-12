package club.ttg.subscriber.domain.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Скидка за оплату нескольких месяцев сразу: от 3 месяцев — 3%, от 6 — 6%,
 * от 12 — 9%. Отдельно — пасхалка: от {@link #EASTER_EGG} месяцев скидка больше
 * (сайт её не рекламирует, срок набирают вручную). Сумма считается только
 * здесь, сайт лишь показывает её по ступеням из условий оплаты.
 */
public final class SubscriptionDiscount {
    /** Ступени скидки по возрастанию срока. */
    public static final List<Tier> TIERS = List.of(
            new Tier(3, 3),
            new Tier(6, 6),
            new Tier(12, 9));

    /** Пасхалка: скрытая ступень, сайт не показывает её в подсказках. */
    public static final Tier EASTER_EGG = new Tier(36, 15);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SubscriptionDiscount() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Скидка в процентах за срок: пасхалка, если срок до неё дотянул, иначе
     * наибольшая из ступеней, до которых срок дотянул.
     */
    public static int percentFor(int months) {
        if (months >= EASTER_EGG.fromMonths()) {
            return EASTER_EGG.percent();
        }
        int percent = 0;
        for (Tier tier : TIERS) {
            if (months >= tier.fromMonths()) {
                percent = tier.percent();
            }
        }
        return percent;
    }

    /** Сумма за срок со скидкой, округлённая до копеек (HALF_UP). */
    public static BigDecimal amountFor(BigDecimal monthPrice, int months) {
        BigDecimal full = monthPrice.multiply(BigDecimal.valueOf(months));
        BigDecimal factor = HUNDRED.subtract(BigDecimal.valueOf(percentFor(months))).divide(HUNDRED);
        return full.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Ступень скидки.
     *
     * @param fromMonths с какого срока действует
     * @param percent    скидка в процентах
     */
    public record Tier(int fromMonths, int percent) {
    }
}
