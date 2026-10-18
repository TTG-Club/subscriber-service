package club.ttg.subscriber.domain.payment.service;

import club.ttg.subscriber.domain.payment.model.PaymentSettings;
import club.ttg.subscriber.domain.payment.repository.PaymentSettingsRepository;
import club.ttg.subscriber.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Выключатель оплаты подписки из админки. Пока оплата выключена, сервис не
 * создаёт платежи — даже на запрос в обход сайта. Уведомления PayAnyWay по уже
 * созданным платежам обрабатываются как обычно: деньги за них могут прийти и после
 * выключения.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAvailabilityService {
    private final PaymentSettingsRepository paymentSettingsRepository;

    /** Включена ли оплата. Нет строки настроек — оплата включена, как до появления выключателя. */
    @Transactional(readOnly = true)
    public boolean isSalesEnabled() {
        return paymentSettingsRepository.findById(PaymentSettings.SINGLETON_ID)
                .map(PaymentSettings::isSalesEnabled)
                .orElse(true);
    }

    /**
     * Включает или выключает оплату и запоминает, кто переключил.
     *
     * @param enabled новое состояние
     * @return сохранённое состояние
     */
    @Transactional
    public boolean setSalesEnabled(boolean enabled) {
        PaymentSettings paymentSettings = paymentSettingsRepository.findById(PaymentSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    PaymentSettings created = new PaymentSettings();
                    created.setId(PaymentSettings.SINGLETON_ID);
                    return created;
                });
        String username = SecurityUtils.currentUsername();
        paymentSettings.setSalesEnabled(enabled);
        paymentSettings.setUpdatedBy(username);
        paymentSettingsRepository.save(paymentSettings);
        log.info("Оплата подписки {} пользователем {}", enabled ? "включена" : "выключена", username);

        return enabled;
    }
}
