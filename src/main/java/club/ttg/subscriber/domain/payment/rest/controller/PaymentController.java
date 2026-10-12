package club.ttg.subscriber.domain.payment.rest.controller;

import club.ttg.subscriber.domain.payment.rest.dto.CreatePaymentRequest;
import club.ttg.subscriber.domain.payment.rest.dto.CreatePaymentResponse;
import club.ttg.subscriber.domain.payment.rest.dto.PaymentTermsResponse;
import club.ttg.subscriber.domain.payment.service.PaymentNotificationResult;
import club.ttg.subscriber.domain.payment.service.PaymentReceipt;
import club.ttg.subscriber.domain.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Оплата подписки через PayAnyWay. Создание платежа — для авторизованного пользователя;
 * уведомление об оплате (Pay URL) открыто без JWT и защищено подписью MONETA.Assistant.
 */
@Tag(name = "Оплата подписки")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions/payments")
public class PaymentController {
    public static final String NOTIFICATION_PATH = "/api/subscriptions/payments/payanyway";
    /** Ставка НДС в чеке: «НДС не облагается» (самозанятый). */
    private static final int VAT_NOT_APPLICABLE = 1105;

    private final PaymentService paymentService;

    @Secured("USER")
    @Operation(summary = "Условия оплаты подписки: цена месяца, валюта, предел срока")
    @GetMapping("/terms")
    public PaymentTermsResponse terms() {
        return paymentService.terms();
    }

    @Secured("USER")
    @Operation(summary = "Создать платёж за подписку на N месяцев и получить ссылку на форму оплаты")
    @PostMapping
    public CreatePaymentResponse create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        return paymentService.create(request, authorization);
    }

    @Operation(summary = "Уведомление PayAnyWay об оплате (Pay URL)")
    @RequestMapping(value = "/payanyway", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> notification(@RequestParam Map<String, String> params) {
        PaymentNotificationResult result = paymentService.handleNotification(params);
        if (result.isFail()) {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .body("FAIL");
        }

        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<MNT_RESPONSE>"
                + "<MNT_ID>" + xml(result.accountId()) + "</MNT_ID>"
                + "<MNT_TRANSACTION_ID>" + xml(result.transactionId()) + "</MNT_TRANSACTION_ID>"
                + "<MNT_RESULT_CODE>" + result.resultCode() + "</MNT_RESULT_CODE>"
                + "<MNT_SIGNATURE>" + paymentService.responseSignature(result) + "</MNT_SIGNATURE>"
                + receiptAttributes(result.receipt())
                + "</MNT_RESPONSE>";

        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8))
                .body(body);
    }

    /**
     * Данные для чека в ответе на уведомление: позиция ({@code INVENTORY}) и почта
     * покупателя ({@code CUSTOMER}), на которую PayAnyWay отправит чек. В подпись
     * ответа атрибуты не входят.
     */
    private static String receiptAttributes(PaymentReceipt receipt) {
        if (receipt == null) {
            return "";
        }

        String inventory = "[{\"name\":\"" + json(receipt.item()) + "\","
                + "\"price\":" + receipt.amount().toPlainString() + ","
                + "\"quantity\":1,"
                + "\"vatTag\":" + VAT_NOT_APPLICABLE + ","
                + "\"pm\":\"full_payment\","
                + "\"po\":\"service\"}]";

        StringBuilder attributes = new StringBuilder("<MNT_ATTRIBUTES>")
                .append(attribute("INVENTORY", inventory));
        if (receipt.email() != null) {
            attributes.append(attribute("CUSTOMER", receipt.email()));
        }
        return attributes.append("</MNT_ATTRIBUTES>").toString();
    }

    private static String attribute(String key, String value) {
        return "<ATTRIBUTE><KEY>" + key + "</KEY><VALUE>" + xml(value) + "</VALUE></ATTRIBUTE>";
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String xml(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
