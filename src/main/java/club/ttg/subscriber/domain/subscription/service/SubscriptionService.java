package club.ttg.subscriber.domain.subscription.service;

import club.ttg.subscriber.client.AuthServiceRoleClient;
import club.ttg.subscriber.client.CoreApiAchievementClient;
import club.ttg.subscriber.domain.subscription.model.RedemptionCode;
import club.ttg.subscriber.domain.subscription.model.RewardPerk;
import club.ttg.subscriber.domain.subscription.model.RewardResource;
import club.ttg.subscriber.domain.subscription.model.SubscriptionType;
import club.ttg.subscriber.domain.subscription.model.UserSubscription;
import club.ttg.subscriber.domain.subscription.repository.RedemptionCodeRepository;
import club.ttg.subscriber.domain.subscription.repository.UserSubscriptionRepository;
import club.ttg.subscriber.domain.subscription.rest.dto.AdminSubscriptionStatusResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.CreateCodesRequest;
import club.ttg.subscriber.domain.subscription.rest.dto.MyRedemptionResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.RedeemResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.RedemptionCodeResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.SubscriptionResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.SubscriptionStatusResponse;
import club.ttg.subscriber.exception.ApiException;
import club.ttg.subscriber.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Подписки и одноразовые коды погашения: выпуск/деактивация кодов, погашение и
 * активация подписки пользователем, админская выдача/отзыв и вычисление статуса.
 * При погашении кода с ранним доступом ({@link RewardPerk#EARLY_ACCESS_DOWNLOAD}) сервис
 * просит auth-service выдать роль VTTG (см. {@link AuthServiceRoleClient}); подписочный
 * доступ по-прежнему считается в реальном времени по `startsAt`/`expiresAt`
 * (см. {@link #statusFor}). Погашение кода атомарно защищено от гонок на уровне БД.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 16;
    private static final int MAX_CODE_ATTEMPTS = 20;
    private static final int MAX_BATCH_SIZE = 1000;
    /** Роль раннего доступа, выдаётся auth-service при погашении кода с EARLY_ACCESS_DOWNLOAD. */
    private static final String EARLY_ACCESS_ROLE = "VTTG";

    private final RedemptionCodeRepository codeRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final RewardService rewardService;
    private final CoreApiAchievementClient achievementClient;
    private final AuthServiceRoleClient authRoleClient;
    private final SecureRandom random = new SecureRandom();

    /**
     * Выпускает пачку одноразовых кодов с одинаковым содержимым.
     */
    @Transactional
    public List<RedemptionCodeResponse> createCodes(CreateCodesRequest request) {
        int count = request.count() == null ? 1 : request.count();
        if (count < 1 || count > MAX_BATCH_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Количество кодов должно быть от 1 до " + MAX_BATCH_SIZE);
        }
        boolean hasPerks = request.perks() != null && !request.perks().isEmpty();
        boolean hasAchievements = request.achievements() != null && !request.achievements().isEmpty();
        if (request.subscriptionMonths() == null && request.rewardTier() == null && !hasPerks && !hasAchievements) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Код должен нести подписку, тир, перки или достижения");
        }

        // Подписка: явно заданный в запросе срок важнее; иначе берём пресет тира (тип — GIFT).
        // Срок «зашивается» в код при выпуске, поэтому погашение/статус дальше работают как обычно.
        Integer subscriptionMonths = request.subscriptionMonths();
        SubscriptionType subscriptionType = request.subscriptionType();
        if (subscriptionMonths == null && request.rewardTier() != null
                && request.rewardTier().subscriptionMonths() > 0) {
            subscriptionMonths = request.rewardTier().subscriptionMonths();
            if (subscriptionType == null) {
                subscriptionType = SubscriptionType.GIFT;
            }
        }
        if (subscriptionMonths != null) {
            if (subscriptionMonths < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Срок подписки должен быть не меньше 1 месяца");
            }
            if (subscriptionType == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Не указан тип подписки");
            }
        }

        Set<String> batch = new HashSet<>();
        List<RedemptionCodeResponse> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RedemptionCode code = new RedemptionCode();
            code.setCode(generateUniqueCode(batch));
            code.setSubscriptionType(subscriptionMonths == null ? null : subscriptionType);
            code.setSubscriptionMonths(subscriptionMonths);
            code.setRewardTier(request.rewardTier());
            code.setPerks(hasPerks ? EnumSet.copyOf(request.perks()) : new HashSet<>());
            code.setAchievements(hasAchievements ? new HashSet<>(request.achievements()) : new HashSet<>());
            code.setLabel(request.label());
            result.add(toResponse(codeRepository.save(code)));
        }
        return result;
    }

    /**
     * Погашает код: выдаёт перки сразу (навсегда) и создаёт подписку в статусе
     * REGISTERED, если код её нёс. Таймер запускается отдельно — {@link #activate}.
     * Достижения начисляются best-effort через core-api (см. {@link CoreApiAchievementClient}).
     * <p>
     * Захват кода атомарен ({@link RedemptionCodeRepository#claim}): при гонке двух
     * параллельных погашений ровно одно обновит строку, второе получит 409.
     */
    @Transactional
    public RedeemResponse redeem(String rawCode) {
        String username = currentUsername();
        String normalized = normalizeCode(rawCode);
        RedemptionCode code = codeRepository.findByCode(normalized)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Код не найден"));
        if (code.getRedeemedBy() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Код уже использован");
        }
        if (code.isDisabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "Код деактивирован");
        }

        Instant now = Instant.now();
        if (codeRepository.claim(normalized, username, now) == 0) {
            // строку успел захватить параллельный запрос между findByCode и claim
            throw new ApiException(HttpStatus.CONFLICT, "Код уже использован");
        }

        SubscriptionResponse subscription = null;
        if (code.getSubscriptionMonths() != null) {
            UserSubscription entity = new UserSubscription();
            entity.setType(code.getSubscriptionType());
            entity.setDurationMonths(code.getSubscriptionMonths());
            entity.setOwnerUsername(username);
            entity.setSourceCode(code.getUuid());
            entity.setRegisteredAt(now);
            subscription = toResponse(subscriptionRepository.save(entity), now);
        }

        // перки = пресет тира ∪ произвольный набор кода
        Set<RewardPerk> perks = EnumSet.noneOf(RewardPerk.class);
        if (code.getRewardTier() != null) {
            perks.addAll(code.getRewardTier().perks());
        }
        perks.addAll(code.getPerks());
        List<RewardPerk> grantedPerks = rewardService.grantPerks(username, perks, code.getUuid());

        // Ранний доступ → роль VTTG в auth-service. НЕ best-effort: любая ошибка откатывает
        // всю транзакцию погашения (код останется непогашенным), чтобы не выдать перки/
        // подписку без роли. Грант в auth идемпотентен, поэтому редкий случай «auth выдал,
        // но локальный commit упал» самолечится при повторном погашении.
        if (perks.contains(RewardPerk.EARLY_ACCESS_DOWNLOAD)) {
            try {
                authRoleClient.grantRole(username, EARLY_ACCESS_ROLE);
            } catch (RestClientResponseException httpError) {
                // auth ответил статусом-ошибкой. 4xx (роль/пользователь не найдены, неверный
                // service-token) детерминированны — повтор не поможет; 5xx можно повторить.
                log.error("auth-service отклонил выдачу роли {} пользователю {} по коду {}: {}",
                        EARLY_ACCESS_ROLE, username, code.getUuid(), httpError.getStatusCode());
                if (httpError.getStatusCode().is4xxClientError()) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            "Не удалось активировать код: ошибка выдачи доступа, обратитесь в поддержку");
                }
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Сервис временно недоступен, попробуйте позже");
            } catch (Exception exception) {
                // сеть/таймаут/прочее — транзиентно, повтор может помочь
                log.error("auth-service недоступен при выдаче роли {} пользователю {} по коду {}: {}",
                        EARLY_ACCESS_ROLE, username, code.getUuid(), exception.getMessage());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Сервис временно недоступен, попробуйте позже");
            }
        }

        // достижения живут в core-api: начисляем best-effort, ошибка не валит погашение
        Set<String> achievements = code.getAchievements();
        if (achievements != null && !achievements.isEmpty()) {
            try {
                achievementClient.grant(username, achievements, code.getUuid());
            } catch (Exception exception) {
                log.warn("Не удалось начислить достижения {} пользователю {} по коду {}: {}",
                        achievements, username, code.getUuid(), exception.getMessage());
            }
        }

        List<String> grantedAchievements = achievements == null ? List.of() : new ArrayList<>(achievements);
        return new RedeemResponse(subscription, grantedPerks, grantedAchievements);
    }

    /**
     * Активирует накопленную подписку пользователя: фиксирует даты старта/окончания.
     * Ролей сервис не выдаёт — доступ определяется по статусу подписки в реальном времени.
     */
    @Transactional
    public SubscriptionResponse activate(UUID id) {
        String username = currentUsername();
        UserSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Подписка не найдена"));

        if (!username.equals(subscription.getOwnerUsername())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Подписка зарегистрирована на другого пользователя");
        }
        if (subscription.getStartsAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Подписка уже активирована");
        }

        Instant now = Instant.now();
        subscription.setStartsAt(now);
        subscription.setExpiresAt(ZonedDateTime.ofInstant(now, ZoneOffset.UTC)
                .plusMonths(subscription.getDurationMonths())
                .toInstant());

        return toResponse(subscription, now);
    }

    /** Все подписки для админского списка (новые сверху) с актуальным статусом. */
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> allSubscriptions() {
        Instant now = Instant.now();
        return subscriptionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(subscription -> toResponse(subscription, now))
                .toList();
    }

    /** Все выпущенные коды для админского списка (новые сверху). */
    @Transactional(readOnly = true)
    public List<RedemptionCodeResponse> allCodes() {
        return codeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Мягко деактивирует или возвращает в строй выпущенный код. Деактивированный код
     * нельзя погасить, но запись сохраняется. Менять статус уже использованного кода
     * нельзя. Деактивацию пишем в аудит (кем/когда), при возврате — очищаем.
     */
    @Transactional
    public RedemptionCodeResponse setCodeDisabled(UUID id, boolean disabled) {
        RedemptionCode code = codeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Код не найден"));
        if (code.getRedeemedBy() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Нельзя изменить статус уже использованного кода");
        }

        if (disabled) {
            code.setDisabled(true);
            code.setDisabledAt(Instant.now());
            code.setDisabledBy(currentUsername());
        } else {
            code.setDisabled(false);
            code.setDisabledAt(null);
            code.setDisabledBy(null);
        }
        return toResponse(codeRepository.save(code));
    }

    /** Подписки текущего пользователя (новые сверху) с актуальным статусом. */
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> currentUserSubscriptions() {
        Instant now = Instant.now();
        return subscriptionRepository.findByOwnerUsernameOrderByCreatedAtDesc(currentUsername()).stream()
                .map(subscription -> toResponse(subscription, now))
                .toList();
    }

    /**
     * Сводный статус подписки пользователя для админ-карточки: одна «самая релевантная»
     * запись — активная → ожидающая активации → истёкшая. Возвращает единый объект
     * (а не список), который потребляет админка сайта.
     */
    @Transactional(readOnly = true)
    public AdminSubscriptionStatusResponse adminStatusFor(String username) {
        if (!StringUtils.hasText(username)) {
            return AdminSubscriptionStatusResponse.none();
        }
        Instant now = Instant.now();
        List<UserSubscription> subscriptions =
                subscriptionRepository.findByOwnerUsernameOrderByCreatedAtDesc(username);
        if (subscriptions.isEmpty()) {
            return AdminSubscriptionStatusResponse.none();
        }

        // По наибольшей дате окончания (null — самые «маленькие», т.е. в конце при max).
        Comparator<UserSubscription> byExpires = Comparator.comparing(
                UserSubscription::getExpiresAt, Comparator.nullsFirst(Comparator.naturalOrder()));

        UserSubscription active = subscriptions.stream()
                .filter(subscription -> "ACTIVE".equals(status(subscription, now)))
                .max(byExpires)
                .orElse(null);
        if (active != null) {
            return new AdminSubscriptionStatusResponse(true, true, "ACTIVE",
                    active.getType(), active.getStartsAt(), active.getExpiresAt());
        }

        UserSubscription registered = subscriptions.stream()
                .filter(subscription -> "REGISTERED".equals(status(subscription, now)))
                .findFirst()
                .orElse(null);
        if (registered != null) {
            return new AdminSubscriptionStatusResponse(false, true, "REGISTERED",
                    registered.getType(), registered.getStartsAt(), registered.getExpiresAt());
        }

        UserSubscription expired = subscriptions.stream()
                .filter(subscription -> "EXPIRED".equals(status(subscription, now)))
                .max(byExpires)
                .orElse(subscriptions.get(0));
        return new AdminSubscriptionStatusResponse(false, true, status(expired, now),
                expired.getType(), expired.getStartsAt(), expired.getExpiresAt());
    }

    /**
     * Реал-тайм статус подписки пользователя. Активна — если есть подписка с
     * проставленным `startsAt` и `expiresAt` в будущем; возвращается с наибольшей датой
     * окончания. Используется фронтом (свой статус) и service-to-service вызовами.
     */
    @Transactional(readOnly = true)
    public SubscriptionStatusResponse statusFor(String username) {
        if (!StringUtils.hasText(username)) {
            return SubscriptionStatusResponse.inactive();
        }
        Instant now = Instant.now();
        boolean registered = subscriptionRepository.existsByOwnerUsername(username);
        return subscriptionRepository
                .findFirstByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(username, now)
                .map(subscription -> new SubscriptionStatusResponse(
                        true,
                        registered,
                        subscription.getExpiresAt(),
                        subscription.getStartsAt(),
                        subscription.getType()))
                .orElseGet(() -> new SubscriptionStatusResponse(false, registered, null, null, null));
    }

    /**
     * Админская выдача/продление подписки на N месяцев. Если активная подписка есть —
     * продлевает её `expiresAt` на `months`; иначе создаёт сразу активную (ACTIVE):
     * `startsAt = now`, `expiresAt = now + months`.
     */
    @Transactional
    public SubscriptionResponse grant(String username, int months) {
        if (!StringUtils.hasText(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Не указан пользователь");
        }
        if (months < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Срок выдачи должен быть не меньше 1 месяца");
        }

        Instant now = Instant.now();
        UserSubscription subscription = subscriptionRepository
                .findFirstByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(username, now)
                .orElse(null);

        if (subscription == null) {
            subscription = new UserSubscription();
            subscription.setType(SubscriptionType.GIFT);
            subscription.setDurationMonths(months);
            subscription.setOwnerUsername(username);
            subscription.setRegisteredAt(now);
            subscription.setStartsAt(now);
            subscription.setExpiresAt(plusMonths(now, months));
        } else {
            Instant base = subscription.getExpiresAt() == null ? now : subscription.getExpiresAt();
            subscription.setExpiresAt(plusMonths(base, months));
            subscription.setDurationMonths(subscription.getDurationMonths() + months);
        }

        return toResponse(subscriptionRepository.save(subscription), now);
    }

    /**
     * Админское немедленное отключение: всем активным подпискам пользователя
     * проставляет `expiresAt = now`.
     *
     * @return затронутые (отключённые) подписки в актуальном статусе
     */
    @Transactional
    public List<SubscriptionResponse> revoke(String username) {
        if (!StringUtils.hasText(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Не указан пользователь");
        }
        Instant now = Instant.now();
        List<SubscriptionResponse> revoked = new ArrayList<>();
        for (UserSubscription subscription : subscriptionRepository.findByOwnerUsernameOrderByCreatedAtDesc(username)) {
            boolean active = subscription.getStartsAt() != null
                    && subscription.getExpiresAt() != null
                    && subscription.getExpiresAt().isAfter(now);
            if (!active) {
                continue;
            }
            subscription.setExpiresAt(now);
            revoked.add(toResponse(subscriptionRepository.save(subscription), now));
        }
        return revoked;
    }

    /**
     * Коды, погашенные текущим пользователем (новые сверху), с резолвом наград в
     * ссылки и привязанной подпиской — для раздела «Активация кодов» в кабинете.
     * Награды кода = пресет тира ∪ доп. перки кода (как при погашении), что даёт
     * стабильный перечень ссылок независимо от дедупликации выданных перков.
     */
    @Transactional(readOnly = true)
    public List<MyRedemptionResponse> currentUserRedemptions() {
        String username = currentUsername();
        Instant now = Instant.now();

        // подписки пользователя по коду-источнику — чтобы прицепить к своей строке без N+1
        Map<UUID, UserSubscription> subscriptionsByCode = new HashMap<>();
        for (UserSubscription subscription : subscriptionRepository.findByOwnerUsernameOrderByCreatedAtDesc(username)) {
            if (subscription.getSourceCode() != null) {
                subscriptionsByCode.putIfAbsent(subscription.getSourceCode(), subscription);
            }
        }

        // справочник ссылок на награды — один раз на весь список (а не на каждый код)
        Map<RewardPerk, RewardResource> resources = rewardService.resourceMap();

        return codeRepository.findByRedeemedByOrderByRedeemedAtDesc(username).stream()
                .map(code -> {
                    Set<RewardPerk> perks = EnumSet.noneOf(RewardPerk.class);
                    if (code.getRewardTier() != null) {
                        perks.addAll(code.getRewardTier().perks());
                    }
                    perks.addAll(code.getPerks());

                    UserSubscription subscription = subscriptionsByCode.get(code.getUuid());
                    return new MyRedemptionResponse(
                            code.getUuid(),
                            code.getCode(),
                            code.getRedeemedAt(),
                            code.getRewardTier(),
                            rewardService.describe(perks, code.getRedeemedAt(), resources),
                            code.getAchievements() == null ? Set.of() : new HashSet<>(code.getAchievements()),
                            subscription == null ? null : toResponse(subscription, now));
                })
                .toList();
    }

    private Instant plusMonths(Instant base, int months) {
        return ZonedDateTime.ofInstant(base, ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private String generateUniqueCode(Set<String> batch) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (batch.add(code) && !codeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось создать уникальный код");
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private String currentUsername() {
        String username = SecurityUtils.getUser().getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Пользователь не авторизован");
        }
        return username;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Код обязателен");
        }
        String normalized = code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Код обязателен");
        }
        return normalized;
    }

    private RedemptionCodeResponse toResponse(RedemptionCode code) {
        // Копируем ленивые коллекции в обычные Set внутри транзакции: иначе при сериализации
        // ответа (вне транзакции, open-in-view=false) они бросят LazyInitializationException.
        return new RedemptionCodeResponse(
                code.getUuid(),
                code.getCode(),
                code.getSubscriptionType(),
                code.getSubscriptionMonths(),
                code.getRewardTier(),
                code.getPerks() == null ? Set.of() : new HashSet<>(code.getPerks()),
                code.getAchievements() == null ? Set.of() : new HashSet<>(code.getAchievements()),
                code.getLabel(),
                code.getRedeemedBy(),
                code.getRedeemedAt(),
                code.isDisabled(),
                code.getDisabledAt(),
                code.getDisabledBy(),
                code.getCreatedAt());
    }

    private SubscriptionResponse toResponse(UserSubscription subscription, Instant now) {
        return new SubscriptionResponse(
                subscription.getUuid(),
                subscription.getType(),
                status(subscription, now),
                subscription.getDurationMonths(),
                subscription.getOwnerUsername(),
                subscription.getRegisteredAt(),
                subscription.getStartsAt(),
                subscription.getExpiresAt(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }

    private String status(UserSubscription subscription, Instant now) {
        if (subscription.getOwnerUsername() == null) {
            return "CREATED";
        }
        if (subscription.getStartsAt() == null) {
            return "REGISTERED";
        }
        if (subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(now)) {
            return "ACTIVE";
        }
        return "EXPIRED";
    }
}
