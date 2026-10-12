package club.ttg.subscriber.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Подписи MONETA.Assistant: MD5 от склейки полей в строго заданном порядке с кодом
 * проверки целостности в конце. Отсутствующее необязательное поле (например,
 * {@code MNT_SUBSCRIBER_ID}) просто пропускается.
 */
public final class PayAnyWaySignature {
    private PayAnyWaySignature() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Подпись запроса к платёжной форме:
     * {@code MD5(MNT_ID + MNT_TRANSACTION_ID + MNT_AMOUNT + MNT_CURRENCY_CODE + MNT_SUBSCRIBER_ID + MNT_TEST_MODE + код)}.
     */
    public static String request(String accountId, String transactionId, String amount, String currency,
                                 String subscriberId, String testMode, String integrityCode) {
        return md5(accountId, transactionId, amount, currency, subscriberId, testMode, integrityCode);
    }

    /**
     * Подпись уведомления об оплате (Pay URL):
     * {@code MD5(MNT_ID + MNT_TRANSACTION_ID + MNT_OPERATION_ID + MNT_AMOUNT + MNT_CURRENCY_CODE
     * + MNT_SUBSCRIBER_ID + MNT_TEST_MODE + код)}.
     */
    public static String notification(String accountId, String transactionId, String operationId, String amount,
                                      String currency, String subscriberId, String testMode, String integrityCode) {
        return md5(accountId, transactionId, operationId, amount, currency, subscriberId, testMode, integrityCode);
    }

    /**
     * Подпись XML-ответа на уведомление:
     * {@code MD5(MNT_RESULT_CODE + MNT_ID + MNT_TRANSACTION_ID + код)}.
     */
    public static String response(String resultCode, String accountId, String transactionId, String integrityCode) {
        return md5(resultCode, accountId, transactionId, integrityCode);
    }

    /** Сравнение подписей без утечки по времени и без учёта регистра hex-строки. */
    public static boolean matches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                provided.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private static String md5(String... parts) {
        StringBuilder source = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                source.append(part);
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 недоступен", exception);
        }
    }
}
