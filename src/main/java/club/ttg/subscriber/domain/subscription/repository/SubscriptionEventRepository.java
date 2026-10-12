package club.ttg.subscriber.domain.subscription.repository;

import club.ttg.subscriber.domain.subscription.model.SubscriptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Доступ к истории подписок пользователей.
 */
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {
    /** История подписки пользователя, новые события сверху. */
    List<SubscriptionEvent> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
}
