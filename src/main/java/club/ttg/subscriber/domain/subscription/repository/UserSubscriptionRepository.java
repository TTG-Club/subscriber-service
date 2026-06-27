package club.ttg.subscriber.domain.subscription.repository;

import club.ttg.subscriber.domain.subscription.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    List<UserSubscription> findAllByOrderByCreatedAtDesc();

    List<UserSubscription> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

    boolean existsByOwnerUsername(String ownerUsername);

    boolean existsByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfter(String ownerUsername, Instant now);

    /**
     * Активная подписка пользователя с наибольшей датой окончания.
     * Активной считается подписка с проставленным `startsAt` и `expiresAt` в будущем.
     */
    Optional<UserSubscription> findFirstByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(
            String ownerUsername, Instant now);
}
