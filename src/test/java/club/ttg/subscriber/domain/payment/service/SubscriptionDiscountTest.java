package club.ttg.subscriber.domain.payment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionDiscountTest {
    private static final BigDecimal PRICE = new BigDecimal("300");

    @Test
    void percentGrowsByTiersAndStopsAtNine() {
        assertThat(SubscriptionDiscount.percentFor(1)).isZero();
        assertThat(SubscriptionDiscount.percentFor(2)).isZero();
        assertThat(SubscriptionDiscount.percentFor(3)).isEqualTo(3);
        assertThat(SubscriptionDiscount.percentFor(5)).isEqualTo(3);
        assertThat(SubscriptionDiscount.percentFor(6)).isEqualTo(6);
        assertThat(SubscriptionDiscount.percentFor(11)).isEqualTo(6);
        assertThat(SubscriptionDiscount.percentFor(12)).isEqualTo(9);
        assertThat(SubscriptionDiscount.percentFor(24)).isEqualTo(9);
        assertThat(SubscriptionDiscount.percentFor(37)).isEqualTo(9);
    }

    @Test
    void exactlyThirtySixMonthsIsEasterEgg() {
        assertThat(SubscriptionDiscount.percentFor(36)).isEqualTo(15);
        assertThat(SubscriptionDiscount.amountFor(PRICE, 36)).isEqualByComparingTo("9180.00");
    }

    @Test
    void amountAppliesDiscount() {
        assertThat(SubscriptionDiscount.amountFor(PRICE, 1)).isEqualByComparingTo("300.00");
        assertThat(SubscriptionDiscount.amountFor(PRICE, 3)).isEqualByComparingTo("873.00");
        assertThat(SubscriptionDiscount.amountFor(PRICE, 6)).isEqualByComparingTo("1692.00");
        assertThat(SubscriptionDiscount.amountFor(PRICE, 12)).isEqualByComparingTo("3276.00");
        assertThat(SubscriptionDiscount.amountFor(new BigDecimal("11"), 3)).isEqualByComparingTo("32.01");
    }
}
