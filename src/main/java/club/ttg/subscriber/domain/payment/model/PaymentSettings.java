package club.ttg.subscriber.domain.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Настройки оплаты, которые меняются из админки без деплоя. Строка одна,
 * с {@link #SINGLETON_ID}.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_settings")
public class PaymentSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Short id;

    /** Выключенная оплата: новые платежи не создаются, кнопка покупки на сайте неактивна. */
    @Column(name = "sales_enabled", nullable = false)
    private boolean salesEnabled;

    /** Кто последним переключил оплату. */
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    @UpdateTimestamp(source = SourceType.DB)
    private Instant updatedAt;
}
