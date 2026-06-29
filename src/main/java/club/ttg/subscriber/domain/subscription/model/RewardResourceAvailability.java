package club.ttg.subscriber.domain.subscription.model;

/**
 * Готовность контента награды к выдаче.
 */
public enum RewardResourceAvailability {
    /** Ссылка/контент доступны. */
    AVAILABLE,
    /** Награда закреплена за пользователем, но контент ещё готовится. */
    COMING_SOON
}
