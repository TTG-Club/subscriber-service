package club.ttg.subscriber.domain.payment.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * Настройки приёма оплаты через PayAnyWay (MONETA.Assistant). Номер счёта и код проверки
 * целостности задаются только переменными окружения; пока они не заданы, оплата выключена.
 */
@Getter
@Component
public class PayAnyWaySettings {
    /** Номер расширенного счёта магазина ({@code MNT_ID}). */
    private final String accountId;
    /** Код проверки целостности данных из настроек счёта. Секрет: участвует во всех подписях. */
    private final String integrityCode;
    /** Адрес платёжной формы (рабочая площадка или demo.moneta.ru). */
    private final String formUrl;
    /** Тестовый режим: деньги не списываются, принимаются только тестовые уведомления. */
    private final boolean testMode;
    /** Оплата доступна только администраторам (на время проверки). */
    private final boolean adminOnly;
    /** Цена одного месяца подписки, в рублях. */
    private final BigDecimal price;
    /** Сколько месяцев можно оплатить за раз; 0 — без ограничения. */
    private final int maxMonths;
    /** Адрес сайта, куда платёжная форма вернёт пользователя (без завершающего слэша). */
    private final String siteUrl;

    public PayAnyWaySettings(
            @Value("${payanyway.account-id:}") String accountId,
            @Value("${payanyway.integrity-code:}") String integrityCode,
            @Value("${payanyway.form-url}") String formUrl,
            @Value("${payanyway.test-mode:true}") boolean testMode,
            @Value("${payanyway.admin-only:true}") boolean adminOnly,
            @Value("${payanyway.price:300.00}") BigDecimal price,
            @Value("${payanyway.max-months:0}") int maxMonths,
            @Value("${payanyway.site-url:}") String siteUrl
    ) {
        this.accountId = accountId == null ? "" : accountId.trim();
        this.integrityCode = integrityCode == null ? "" : integrityCode;
        this.formUrl = formUrl;
        this.testMode = testMode;
        this.adminOnly = adminOnly;
        this.price = price;
        this.maxMonths = maxMonths;
        this.siteUrl = StringUtils.trimTrailingCharacter(siteUrl == null ? "" : siteUrl.trim(), '/');
    }

    /** Ограничен ли срок одной оплаты. */
    public boolean hasMaxMonths() {
        return maxMonths > 0;
    }

    /** Заданы ли номер счёта и код проверки целостности. */
    public boolean isConfigured() {
        return StringUtils.hasText(accountId) && StringUtils.hasText(integrityCode);
    }
}
