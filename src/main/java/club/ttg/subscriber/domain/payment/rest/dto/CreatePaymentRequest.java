package club.ttg.subscriber.domain.payment.rest.dto;

import club.ttg.subscriber.domain.payment.model.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Запрос на оплату подписки. Верхняя граница срока задаётся настройкой
 * {@code payanyway.max-months} и проверяется в сервисе.
 *
 * @param months сколько месяцев оплатить (не меньше 1)
 * @param email  почта для чека: по умолчанию почта аккаунта, пользователь может её поменять
 * @param method способ оплаты, экран которого сразу откроет платёжная форма
 */
public record CreatePaymentRequest(
        @NotNull @Min(1) Integer months,
        @NotBlank(message = "Укажите почту для чека")
        @Email(message = "Некорректная почта для чека")
        @Size(max = 255, message = "Почта для чека слишком длинная")
        String email,
        @NotNull(message = "Выберите способ оплаты") PaymentMethod method
) {
}
