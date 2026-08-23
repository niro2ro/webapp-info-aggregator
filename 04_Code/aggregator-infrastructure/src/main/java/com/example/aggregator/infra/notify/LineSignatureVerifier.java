package com.example.aggregator.infra.notify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * LINE Webhook の署名検証（{@code X-Line-Signature}）。リクエスト本文(JSON)を<b>チャネルシークレット</b>で
 * HMAC-SHA256 し Base64 化した値が、ヘッダの署名と一致すれば「LINE からの正規リクエスト」と確認できる
 * （なりすまし防止・公式手順）。SDK に依存せず標準の {@code javax.crypto} で実装する（依存を増やさない）。
 */
@Component
public class LineSignatureVerifier {

    /** {@code body} を {@code channelSecret} で HMAC-SHA256→Base64 し、{@code signature} と定数時間比較する。 */
    public boolean verify(String channelSecret, String body, String signature) {
        if (channelSecret == null || channelSecret.isBlank() || signature == null || body == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(digest);
            // タイミング攻撃を避けるため定数時間比較
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
