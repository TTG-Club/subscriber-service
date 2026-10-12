package club.ttg.subscriber.domain.subscription.service;

import club.ttg.subscriber.domain.subscription.model.SubscriptionEvent;
import club.ttg.subscriber.domain.subscription.model.SubscriptionEventType;
import club.ttg.subscriber.domain.subscription.repository.SubscriptionEventRepository;
import club.ttg.subscriber.domain.subscription.rest.dto.SubscriptionEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * История подписки пользователя для админки: оплаты, выдачи и отключения
 * администратором, промокоды — с тем, как менялась дата окончания. События пишутся
 * в той же транзакции, что и само изменение подписки.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionHistoryService {
    private final SubscriptionEventRepository eventRepository;

    /**
     * Записывает событие истории.
     *
     * @param change что произошло и как изменилась подписка
     */
    public void record(Change change) {
        SubscriptionEvent event = new SubscriptionEvent();
        event.setOwnerUsername(change.username());
        event.setType(change.type());
        event.setMonths(change.months());
        event.setExpiresBefore(change.expiresBefore());
        event.setExpiresAfter(change.expiresAfter());
        event.setActorUsername(change.actorUsername());
        event.setCode(change.code());
        event.setAmount(change.amount());
        eventRepository.save(event);
    }

    /** История подписки пользователя, новые события сверху. */
    @Transactional(readOnly = true)
    public List<SubscriptionEventResponse> historyFor(String username) {
        if (!StringUtils.hasText(username)) {
            return List.of();
        }
        return eventRepository.findByOwnerUsernameOrderByCreatedAtDesc(username).stream()
                .map(event -> new SubscriptionEventResponse(
                        event.getUuid(),
                        event.getType(),
                        event.getMonths(),
                        event.getExpiresBefore(),
                        event.getExpiresAfter(),
                        event.getActorUsername(),
                        event.getCode(),
                        event.getAmount(),
                        event.getCreatedAt()))
                .toList();
    }

    /**
     * Изменение подписки для записи в историю.
     *
     * @param username      чья подписка изменилась
     * @param type          что произошло
     * @param months        на сколько месяцев (null — для отключения)
     * @param expiresBefore окончание действующей подписки до изменения
     * @param expiresAfter  окончание действующей подписки после изменения
     * @param actorUsername кто совершил действие
     * @param code          промокод, если изменение связано с ним
     * @param amount        сумма оплаты, если изменение — оплата
     */
    public record Change(
            String username,
            SubscriptionEventType type,
            Integer months,
            Instant expiresBefore,
            Instant expiresAfter,
            String actorUsername,
            String code,
            BigDecimal amount
    ) {
    }
}
