package club.ttg.subscriber.domain.payment.model;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Платёж за подписку через PayAnyWay. Идентификатор платежа уходит в платёжную форму
 * как {@code MNT_TRANSACTION_ID} и возвращается в уведомлении об оплате (Pay URL).
 */
@Getter
@Setter
@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @UuidGenerator
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    /** Пользователь, которому продлевается подписка (username из auth-service). */
    @Column(nullable = false)
    private String username;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /** На сколько месяцев продлевается подписка после оплаты. */
    @Column(nullable = false)
    private Integer months;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    /** Создан ли платёж в тестовом режиме (без реального списания). */
    @Column(name = "test_mode", nullable = false)
    private boolean testMode;

    /** Номер операции в MONETA.RU ({@code MNT_OPERATION_ID}). */
    @Column(name = "operation_id", length = 64)
    private String operationId;

    /** Подписка, выданная по этому платежу. */
    @Column(name = "subscription_uuid")
    private UUID subscriptionUuid;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp(source = SourceType.DB)
    private Instant createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp(source = SourceType.DB)
    private Instant updatedAt;
}
