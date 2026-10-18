package club.ttg.subscriber.domain.payment.rest.controller;

import club.ttg.subscriber.domain.payment.rest.dto.PaymentAvailabilityResponse;
import club.ttg.subscriber.domain.payment.rest.dto.UpdatePaymentAvailabilityRequest;
import club.ttg.subscriber.domain.payment.service.PaymentAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Управление оплатой подписки из админки.
 */
@Tag(name = "Оплата подписки (админ)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {
    private final PaymentAvailabilityService paymentAvailabilityService;

    @Secured("ADMIN")
    @Operation(summary = "Включить или выключить оплату подписки для всех")
    @PutMapping("/availability")
    public PaymentAvailabilityResponse updateAvailability(@Valid @RequestBody UpdatePaymentAvailabilityRequest request) {
        return new PaymentAvailabilityResponse(paymentAvailabilityService.setSalesEnabled(request.enabled()));
    }
}
