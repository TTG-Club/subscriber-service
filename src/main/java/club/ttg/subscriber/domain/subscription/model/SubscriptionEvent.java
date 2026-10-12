package club.ttg.subscriber.domain.subscription.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Запись истории подписки пользователя: что произошло и как изменилась дата
 * окончания действующей подписки. Только добавляется, не меняется.
 */
@Getter
@Setter
@Entity
@Table(name = "subscription_event")
public class SubscriptionEvent {
    @Id
    @UuidGenerator
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "owner_username", nullable = false)
    private String ownerUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private SubscriptionEventType type;

    /** На сколько месяцев изменилась подписка (null — для отключения). */
    @Column(name = "months")
    private Integer months;

    /** Окончание действующей подписки до события (null — подписки не было). */
    @Column(name = "expires_before")
    private Instant expiresBefore;

    /** Окончание действующей подписки после события (null — подписки нет). */
    @Column(name = "expires_after")
    private Instant expiresAfter;

    /** Кто совершил действие: администратор или сам пользователь. */
    @Column(name = "actor_username")
    private String actorUsername;

    /** Промокод, если событие связано с ним. */
    @Column(name = "code")
    private String code;

    /** Сумма оплаты в рублях, если событие — оплата. */
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp(source = SourceType.DB)
    private Instant createdAt;
}
