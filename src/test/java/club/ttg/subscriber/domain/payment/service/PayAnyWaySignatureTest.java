package club.ttg.subscriber.domain.payment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayAnyWaySignatureTest {
    private static final String TRANSACTION = "0b4c3c1e-1111-2222-3333-444455556666";

    @Test
    void requestSignatureSkipsMissingSubscriberId() {
        assertThat(PayAnyWaySignature.request("12345678", TRANSACTION, "300.00", "RUB", null, "1", "secret"))
                .isEqualTo("17af57dbbe6290eb741998dc2c2d60f5");
    }

    @Test
    void notificationSignatureIncludesOperationId() {
        assertThat(PayAnyWaySignature.notification("12345678", TRANSACTION, "987654", "300.00", "RUB", null, "1",
                "secret"))
                .isEqualTo("11f98c299d3e44d19345c681941a97da");
    }

    @Test
    void responseSignatureStartsWithResultCode() {
        assertThat(PayAnyWaySignature.response("200", "12345678", TRANSACTION, "secret"))
                .isEqualTo("edcf02b190d8b0b6f5d9f33635ead50b");
    }

    @Test
    void matchesIgnoresCase() {
        assertThat(PayAnyWaySignature.matches("edcf02b190d8b0b6f5d9f33635ead50b", "EDCF02B190D8B0B6F5D9F33635EAD50B"))
                .isTrue();
        assertThat(PayAnyWaySignature.matches("edcf02b190d8b0b6f5d9f33635ead50b", null)).isFalse();
    }
}
