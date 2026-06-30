package club.ttg.subscriber.domain.subscription.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Краудфандинговый тир. Перки кумулятивны: каждый следующий тир включает все награды
 * нижестоящих. Срок подписки, наоборот, задаётся для каждого тира отдельно (не
 * накапливается). Порядок объявления значений = возрастание тира.
 */
public enum RewardTier {
    /** Ранний доступ к скачиванию и 3 месяца подписки. */
    TIER_1(3, RewardPerk.EARLY_ACCESS_DOWNLOAD),
    /** Добавляет скачивание карты и токенов; 3 месяца подписки. */
    TIER_2(3, RewardPerk.MAP_TOKENS_DOWNLOAD),
    /** Добавляет скачивание приключения; 3 месяца подписки. */
    TIER_3(3, RewardPerk.ADVENTURE_DOWNLOAD),
    /** Добавляет доступ в приватный чат разработки; 3 месяца подписки. */
    TIER_4(3, RewardPerk.DEV_CHAT_ACCESS),
    /** Добавляет значок профиля и рамку аватарки; 6 месяцев подписки. */
    TIER_5(6, RewardPerk.PROFILE_BADGE, RewardPerk.AVATAR_FRAME),
    /** Добавляет увековечивание в титрах приложения; 12 месяцев подписки. */
    TIER_6(12, RewardPerk.APP_CREDITS);

    /** Срок подписки-пресета в месяцах; 0 — тир без подписки. Не кумулятивен. */
    private final int subscriptionMonths;
    private final Set<RewardPerk> ownPerks;

    RewardTier(int subscriptionMonths, RewardPerk... perks) {
        this.subscriptionMonths = subscriptionMonths;
        this.ownPerks = perks.length == 0
                ? EnumSet.noneOf(RewardPerk.class)
                : EnumSet.copyOf(List.of(perks));
    }

    /**
     * Срок подписки, заложенный в тир (в месяцах). 0 — тир не несёт подписки.
     * В отличие от {@link #perks()} не накапливается между тирами.
     */
    public int subscriptionMonths() {
        return subscriptionMonths;
    }

    /**
     * Кумулятивный набор перков: данного тира и всех нижестоящих.
     */
    public Set<RewardPerk> perks() {
        EnumSet<RewardPerk> result = EnumSet.noneOf(RewardPerk.class);
        for (RewardTier tier : values()) {
            result.addAll(tier.ownPerks);
            if (tier == this) {
                break;
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
