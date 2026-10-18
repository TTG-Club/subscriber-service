package club.ttg.subscriber.domain.payment.repository;

import club.ttg.subscriber.domain.payment.model.PaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к настройкам оплаты (одна строка).
 */
public interface PaymentSettingsRepository extends JpaRepository<PaymentSettings, Short> {
}
