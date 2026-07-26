package club.ttg.subscriber.domain.subscription.service;

import club.ttg.subscriber.domain.subscription.model.RedemptionCode;
import club.ttg.subscriber.domain.subscription.model.RewardPerk;
import club.ttg.subscriber.domain.subscription.model.RewardResource;
import club.ttg.subscriber.domain.subscription.model.RewardResourceAvailability;
import club.ttg.subscriber.domain.subscription.model.UserReward;
import club.ttg.subscriber.domain.subscription.repository.RedemptionCodeRepository;
import club.ttg.subscriber.domain.subscription.repository.RewardResourceRepository;
import club.ttg.subscriber.domain.subscription.repository.UserRewardRepository;
import club.ttg.subscriber.domain.subscription.rest.dto.RewardResourceResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.UpdateRewardResourceRequest;
import club.ttg.subscriber.domain.subscription.rest.dto.UserRewardResponse;
import club.ttg.subscriber.exception.ApiException;
import club.ttg.subscriber.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Выдача и резолв косметических наград (перков). Перки выдаются навсегда и идемпотентно
 * (повторная выдача не дублирует), а их контент — заголовок/ссылка/статус готовности —
 * хранится отдельно в {@link RewardResource} и подставляется при отдаче пользователю.
 */
@Service
@RequiredArgsConstructor
public class RewardService {
    private final UserRewardRepository rewardRepository;
    private final RewardResourceRepository resourceRepository;
    private final RedemptionCodeRepository codeRepository;

    /**
     * Выдаёт пользователю произвольный набор перков. Уже имеющиеся перки пропускаются.
     * Идемпотентно — повторная выдача не дублирует награды.
     *
     * @return фактически выданные перки
     */
    @Transactional
    public List<RewardPerk> grantPerks(String username, Collection<RewardPerk> perks, UUID sourceCode) {
        if (perks == null || perks.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<RewardPerk> granted = new ArrayList<>();
        // EnumSet сортирует по объявлению перков — детерминированный порядок выдачи
        Set<RewardPerk> ordered = EnumSet.copyOf(perks);
        for (RewardPerk perk : ordered) {
            if (rewardRepository.existsByUsernameAndPerk(username, perk)) {
                continue;
            }
            UserReward reward = new UserReward();
            reward.setUsername(username);
            reward.setPerk(perk);
            reward.setGrantedAt(now);
            reward.setSourceCode(sourceCode);
            rewardRepository.save(reward);
            granted.add(perk);
        }
        return granted;
    }

    /** Награды текущего пользователя (новые сверху) с подставленным контентом. */
    @Transactional(readOnly = true)
    public List<UserRewardResponse> currentUserRewards() {
        Map<RewardPerk, RewardResource> resources = resourceMap();
        List<UserReward> rewards = rewardRepository.findByUsernameOrderByGrantedAtDesc(currentUsername());
        Map<UUID, String> inviteLinks = devChatInviteLinks(rewards);
        return rewards.stream()
                .map(reward -> toResponse(reward, resources.get(reward.getPerk()),
                        reward.getSourceCode() == null ? null : inviteLinks.get(reward.getSourceCode())))
                .toList();
    }

    /**
     * Персональные приглашения в чат разработки по кодам-источникам наград
     * {@link RewardPerk#DEV_CHAT_ACCESS} (uuid кода → ссылка). Один батч-запрос
     * вместо чтения кода на каждую награду; практически наград с этим перком у
     * пользователя не больше одной.
     */
    private Map<UUID, String> devChatInviteLinks(List<UserReward> rewards) {
        List<UUID> sourceCodes = rewards.stream()
                .filter(reward -> reward.getPerk() == RewardPerk.DEV_CHAT_ACCESS)
                .map(UserReward::getSourceCode)
                .filter(Objects::nonNull)
                .toList();
        if (sourceCodes.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> links = new HashMap<>();
        for (RedemptionCode code : codeRepository.findAllById(sourceCodes)) {
            if (StringUtils.hasText(code.getTelegramInviteLink())) {
                links.put(code.getUuid(), code.getTelegramInviteLink());
            }
        }
        return links;
    }

    /**
     * Описывает набор перков их контентом (ссылка/статус готовности) — без привязки
     * к конкретной выдаче. Нужен для показа «что даёт код» в личном кабинете: для
     * каждого перка подставляются title/url/availability из {@link RewardResource}.
     * Порядок — по объявлению перков (детерминированный). Готовую карту ресурсов
     * берут через {@link #resourceMap()} один раз и переиспользуют для многих кодов,
     * чтобы не сканировать reward_resource на каждый код.
     *
     * @param grantedAt         момент получения (например, дата погашения кода)
     * @param devChatInviteLink одноразовое приглашение кода в чат разработки: для
     *                          {@link RewardPerk#DEV_CHAT_ACCESS} оно важнее общей
     *                          ссылки/готовности из ресурса (null — у кода его нет)
     */
    public List<UserRewardResponse> describe(Collection<RewardPerk> perks, Instant grantedAt,
                                             Map<RewardPerk, RewardResource> resources,
                                             String devChatInviteLink) {
        if (perks == null || perks.isEmpty()) {
            return List.of();
        }
        return EnumSet.copyOf(perks).stream()
                .map(perk -> describe(perk, grantedAt, resources.get(perk), devChatInviteLink))
                .toList();
    }

    private UserRewardResponse describe(RewardPerk perk, Instant grantedAt, RewardResource resource,
                                        String devChatInviteLink) {
        return new UserRewardResponse(
                perk,
                grantedAt,
                resource == null ? null : resource.getTitle(),
                resolveUrl(perk, resource, devChatInviteLink),
                resolveAvailability(perk, resource, devChatInviteLink),
                resource == null ? null : resource.getNote());
    }

    /** Ссылка награды: персональное приглашение в чат (если есть) важнее общей из ресурса. */
    private static String resolveUrl(RewardPerk perk, RewardResource resource, String devChatInviteLink) {
        if (perk == RewardPerk.DEV_CHAT_ACCESS && StringUtils.hasText(devChatInviteLink)) {
            return devChatInviteLink;
        }
        return resource == null ? null : resource.getUrl();
    }

    /**
     * Готовность награды: персональное приглашение готово по определению, даже если
     * общий ресурс перка помечен «Скоро» (COMING_SOON у ресурса описывает общий контент
     * и правится админом в рантайме — он не должен прятать уже выпущенную ссылку кода).
     */
    private static RewardResourceAvailability resolveAvailability(RewardPerk perk, RewardResource resource,
                                                                  String devChatInviteLink) {
        if (perk == RewardPerk.DEV_CHAT_ACCESS && StringUtils.hasText(devChatInviteLink)) {
            return RewardResourceAvailability.AVAILABLE;
        }
        return resource == null ? null : resource.getAvailability();
    }

    /** Весь конфиг контента наград (perk → ссылка/статус готовности) для админки. */
    @Transactional(readOnly = true)
    public List<RewardResourceResponse> resources() {
        return resourceRepository.findAll().stream()
                .map(this::toResourceResponse)
                .toList();
    }

    /** Создаёт или обновляет контент награды (ссылку/статус готовности) для перка. */
    @Transactional
    public RewardResourceResponse updateResource(RewardPerk perk, UpdateRewardResourceRequest request) {
        RewardResource resource = resourceRepository.findById(perk).orElseGet(() -> {
            RewardResource created = new RewardResource();
            created.setPerk(perk);
            return created;
        });
        resource.setTitle(request.title());
        resource.setUrl(request.url());
        resource.setAvailability(request.availability());
        resource.setNote(request.note());
        return toResourceResponse(resourceRepository.save(resource));
    }

    /** Пользователи, увековеченные в приложении (перк APP_CREDITS). */
    @Transactional(readOnly = true)
    public List<String> supporters() {
        return rewardRepository.findByPerkOrderByGrantedAtAsc(RewardPerk.APP_CREDITS).stream()
                .map(UserReward::getUsername)
                .distinct()
                .toList();
    }

    /** Справочник контента наград (perk → ресурс). Публичный для батч-резолва в других сервисах. */
    @Transactional(readOnly = true)
    public Map<RewardPerk, RewardResource> resourceMap() {
        Map<RewardPerk, RewardResource> map = new EnumMap<>(RewardPerk.class);
        resourceRepository.findAll().forEach(resource -> map.put(resource.getPerk(), resource));
        return map;
    }

    private UserRewardResponse toResponse(UserReward reward, RewardResource resource, String devChatInviteLink) {
        return new UserRewardResponse(
                reward.getPerk(),
                reward.getGrantedAt(),
                resource == null ? null : resource.getTitle(),
                resolveUrl(reward.getPerk(), resource, devChatInviteLink),
                resolveAvailability(reward.getPerk(), resource, devChatInviteLink),
                resource == null ? null : resource.getNote());
    }

    private RewardResourceResponse toResourceResponse(RewardResource resource) {
        return new RewardResourceResponse(
                resource.getPerk(),
                resource.getTitle(),
                resource.getUrl(),
                resource.getAvailability(),
                resource.getNote(),
                resource.getUpdatedAt());
    }

    private String currentUsername() {
        String username = SecurityUtils.getUser().getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Пользователь не авторизован");
        }
        return username;
    }
}
