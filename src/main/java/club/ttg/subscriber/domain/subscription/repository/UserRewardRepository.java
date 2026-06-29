package club.ttg.subscriber.domain.subscription.repository;

import club.ttg.subscriber.domain.subscription.model.RewardPerk;
import club.ttg.subscriber.domain.subscription.model.UserReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Доступ к постоянным наградам, закреплённым за пользователями.
 */
public interface UserRewardRepository extends JpaRepository<UserReward, UUID> {
    /** Награды пользователя, новые сверху (для личного кабинета). */
    List<UserReward> findByUsernameOrderByGrantedAtDesc(String username);

    /** Есть ли у пользователя данный перк (защита от повторной выдачи). */
    boolean existsByUsernameAndPerk(String username, RewardPerk perk);

    /** Все держатели перка, старые сверху (например, для рассылки готового контента). */
    List<UserReward> findByPerkOrderByGrantedAtAsc(RewardPerk perk);
}
