package club.ttg.subscriber.domain.subscription.repository;

import club.ttg.subscriber.domain.subscription.model.RewardPerk;
import club.ttg.subscriber.domain.subscription.model.RewardResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardResourceRepository extends JpaRepository<RewardResource, RewardPerk> {
}
