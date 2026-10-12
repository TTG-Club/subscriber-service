package club.ttg.subscriber.domain.payment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Способ оплаты, который пользователь выбрал на сайте. Платёжная форма PayAnyWay
 * сразу открывает его экран ({@code paymentSystem.unitId}).
 */
@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    /** Банковская карта. */
    CARD("card"),
    /** Система быстрых платежей (QR-код). */
    SBP("sbpc2b");

    /** Идентификатор способа оплаты в MONETA.Assistant. */
    private final String unitId;
}
