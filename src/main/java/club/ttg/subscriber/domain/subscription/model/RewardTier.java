package club.ttg.subscriber.domain.subscription.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Краудфандинговый тир. Тиры кумулятивны: каждый следующий включает все награды
 * нижестоящих. Порядок объявления значений = возрастание тира.
 */
public enum RewardTier {
    /** Ранний доступ к скачиванию. */
    TIER_1(RewardPerk.EARLY_ACCESS_DOWNLOAD),
    /** Добавляет скачивание карты и токенов. */
    TIER_2(RewardPerk.MAP_TOKENS_DOWNLOAD),
    /** Добавляет скачивание приключения. */
    TIER_3(RewardPerk.ADVENTURE_DOWNLOAD),
    /** Добавляет доступ в приватный чат разработки. */
    TIER_4(RewardPerk.DEV_CHAT_ACCESS),
    /** Добавляет значок профиля и рамку аватарки. */
    TIER_5(RewardPerk.PROFILE_BADGE, RewardPerk.AVATAR_FRAME),
    /** Добавляет увековечивание в титрах приложения. */
    TIER_6(RewardPerk.APP_CREDITS);

    private final Set<RewardPerk> ownPerks;

    RewardTier(RewardPerk... perks) {
        this.ownPerks = perks.length == 0
                ? EnumSet.noneOf(RewardPerk.class)
                : EnumSet.copyOf(List.of(perks));
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
