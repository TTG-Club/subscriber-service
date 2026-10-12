package club.ttg.subscriber.domain.payment.repository;

import club.ttg.subscriber.domain.payment.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Доступ к платежам за подписку.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    /**
     * Платёж с блокировкой строки: повторные уведомления об оплате, пришедшие
     * одновременно, обрабатываются по очереди и не продлевают подписку дважды.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.uuid = :uuid")
    Optional<Payment> findForUpdate(@Param("uuid") UUID uuid);
}
