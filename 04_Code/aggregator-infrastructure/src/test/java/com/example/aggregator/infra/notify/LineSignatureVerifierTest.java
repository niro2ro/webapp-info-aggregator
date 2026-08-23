package com.example.aggregator.infra.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LINE Webhook 署名検証（HMAC-SHA256→Base64）の一致/不一致を検証する。 */
class LineSignatureVerifierTest {

    private final LineSignatureVerifier v = new LineSignatureVerifier();

    /** テスト側でも公式手順どおりに署名を計算する（verify が同じ計算で一致するか確かめる）。 */
    private String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("正しい署名は true")
    void validSignature() throws Exception {
        String secret = "channel-secret-xyz";
        String body = "{\"events\":[]}";
        assertThat(v.verify(secret, body, sign(secret, body))).isTrue();
    }

    @Test
    @DisplayName("署名が違えば false")
    void wrongSignature() {
        assertThat(v.verify("s", "{\"events\":[]}", "not-a-valid-signature")).isFalse();
    }

    @Test
    @DisplayName("本文が改ざんされれば false")
    void tamperedBody() throws Exception {
        String secret = "s";
        String sig = sign(secret, "{\"a\":1}");
        assertThat(v.verify(secret, "{\"a\":2}", sig)).isFalse();
    }

    @Test
    @DisplayName("secret/署名が空なら false（検証不能）")
    void blankInputs() {
        assertThat(v.verify(null, "b", "x")).isFalse();
        assertThat(v.verify("", "b", "x")).isFalse();
        assertThat(v.verify("s", "b", null)).isFalse();
    }
}
