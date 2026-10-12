package club.ttg.subscriber.domain.subscription.service;

import club.ttg.subscriber.client.AuthServiceRoleClient;
import club.ttg.subscriber.client.CoreApiAchievementClient;
import club.ttg.subscriber.client.TelegramInviteClient;
import club.ttg.subscriber.domain.subscription.model.SubscriptionEventType;
import club.ttg.subscriber.domain.subscription.model.SubscriptionType;
import club.ttg.subscriber.domain.subscription.model.UserSubscription;
import club.ttg.subscriber.domain.subscription.repository.RedemptionCodeRepository;
import club.ttg.subscriber.domain.subscription.repository.UserSubscriptionRepository;
import club.ttg.subscriber.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceHistoryTest {
    private final UserSubscriptionRepository subscriptionRepository = mock(UserSubscriptionRepository.class);
    private final SubscriptionHistoryService historyService = mock(SubscriptionHistoryService.class);
    private final SubscriptionService service = new SubscriptionService(
            mock(RedemptionCodeRepository.class),
            subscriptionRepository,
            mock(RewardService.class),
            mock(CoreApiAchievementClient.class),
            mock(AuthServiceRoleClient.class),
            mock(TelegramInviteClient.class),
            historyService);

    @BeforeEach
    void signInAdmin() {
        AuthUser admin = new AuthUser(UUID.randomUUID(), "admin", "admin@example.com", List.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        when(subscriptionRepository.save(any(UserSubscription.class))).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantRecordsAdminAndExtendedExpiry() {
        Instant expires = Instant.now().plus(10, ChronoUnit.DAYS);
        UserSubscription active = activeSubscription(expires);
        when(subscriptionRepository.findFirstByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("hero"), any())).thenReturn(Optional.of(active));

        service.grant("hero", 2);

        SubscriptionHistoryService.Change change = recordedChange();
        assertThat(change.type()).isEqualTo(SubscriptionEventType.GRANT);
        assertThat(change.months()).isEqualTo(2);
        assertThat(change.expiresBefore()).isEqualTo(expires);
        assertThat(change.expiresAfter()).isEqualTo(active.getExpiresAt()).isAfter(expires);
        assertThat(change.actorUsername()).isEqualTo("admin");
    }

    @Test
    void purchaseRecordsAmountAndExpiryFromCurrentEnd() {
        Instant expires = Instant.now().plus(5, ChronoUnit.DAYS);
        when(subscriptionRepository.findFirstByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("hero"), any())).thenReturn(Optional.of(activeSubscription(expires)));

        service.purchase("hero", 1, new BigDecimal("300.00"));

        SubscriptionHistoryService.Change change = recordedChange();
        assertThat(change.type()).isEqualTo(SubscriptionEventType.PURCHASE);
        assertThat(change.expiresBefore()).isEqualTo(expires);
        assertThat(change.expiresAfter()).isAfter(expires);
        assertThat(change.amount()).isEqualByComparingTo("300");
        assertThat(change.actorUsername()).isEqualTo("hero");
    }

    @Test
    void revokeRecordsEndOfSubscription() {
        Instant expires = Instant.now().plus(5, ChronoUnit.DAYS);
        when(subscriptionRepository.findFirstByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(
                eq("hero"), any())).thenReturn(Optional.of(activeSubscription(expires)));
        when(subscriptionRepository.findByOwnerUsernameOrderByCreatedAtDesc("hero"))
                .thenReturn(List.of(activeSubscription(expires)));

        service.revoke("hero");

        SubscriptionHistoryService.Change change = recordedChange();
        assertThat(change.type()).isEqualTo(SubscriptionEventType.REVOKE);
        assertThat(change.expiresBefore()).isEqualTo(expires);
        assertThat(change.expiresAfter()).isNull();
        assertThat(change.actorUsername()).isEqualTo("admin");
    }

    @Test
    void revokeWithoutActiveSubscriptionRecordsNothing() {
        when(subscriptionRepository.findByOwnerUsernameOrderByCreatedAtDesc(anyString())).thenReturn(List.of());

        service.revoke("hero");

        verify(historyService, never()).record(any());
    }

    private SubscriptionHistoryService.Change recordedChange() {
        ArgumentCaptor<SubscriptionHistoryService.Change> captor =
                ArgumentCaptor.forClass(SubscriptionHistoryService.Change.class);
        verify(historyService).record(captor.capture());
        return captor.getValue();
    }

    private static UserSubscription activeSubscription(Instant expiresAt) {
        UserSubscription subscription = new UserSubscription();
        subscription.setType(SubscriptionType.GIFT);
        subscription.setDurationMonths(1);
        subscription.setOwnerUsername("hero");
        subscription.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        subscription.setExpiresAt(expiresAt);
        return subscription;
    }
}
