package com.example.aggregator.web;

import com.example.aggregator.infra.notify.LineProperties;
import com.example.aggregator.infra.notify.LineSignatureVerifier;
import com.example.aggregator.infra.service.LineLinkService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * LINE Webhook 受信口（合言葉連携の要）。LINE Developers Console の Webhook URL に
 * {@code https://<公開ホスト>/line/webhook} を設定すると、友だち追加やトーク送信のたびに LINE がここへ POST する。
 *
 * <p>本文が合言葉ならその送信者 {@code userId} を利用者に紐付ける（{@link LineLinkService}）。これにより利用者は
 * 自分の userId を調べる/入力する必要がない。<b>公開URLが前提</b>（localhost では Cloudflare Tunnel 等、
 * 恒久化は Phase 6 の VPS）。なりすまし防止のため必ず署名検証する（{@link LineSignatureVerifier}）。
 *
 * <p>注意: 認証は不要な公開エンドポイント（LINE サーバーが呼ぶ）。Vaadin のルートではなく素の Spring MVC。
 */
@RestController
public class LineWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookController.class);

    private final LineProperties props;
    private final LineSignatureVerifier verifier;
    private final LineLinkService linkService;
    private final ObjectMapper mapper;

    public LineWebhookController(LineProperties props, LineSignatureVerifier verifier,
                                LineLinkService linkService, ObjectMapper mapper) {
        this.props = props;
        this.verifier = verifier;
        this.linkService = linkService;
        this.mapper = mapper;
    }

    @PostMapping("/line/webhook")
    public ResponseEntity<String> receive(@RequestBody(required = false) String body,
                                          @RequestHeader(value = "X-Line-Signature", required = false) String signature) {
        String rawBody = body == null ? "" : body;
        String secret = props.getChannelSecret();
        if (secret == null || secret.isBlank()) {
            // 未設定なら検証できない。設定漏れを気付けるようログのみ（LINE には 200 を返し再送ループを避ける）。
            log.warn("[LINE Webhook] channel-secret 未設定のため検証できません（LINE_CHANNEL_SECRET を設定してください）");
            return ResponseEntity.ok("no-secret");
        }
        if (!verifier.verify(secret, rawBody, signature)) {
            log.warn("[LINE Webhook] 署名不一致のため拒否");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad signature");
        }
        try {
            JsonNode events = mapper.readTree(rawBody).path("events");
            int linked = 0;
            for (JsonNode ev : events) {
                // テキストメッセージのみ扱う（合言葉の受け取り）。follow 等は無視。
                if (!"message".equals(ev.path("type").asText())) continue;
                if (!"text".equals(ev.path("message").path("type").asText())) continue;
                String userId = ev.path("source").path("userId").asText(null);
                String text = ev.path("message").path("text").asText(null);
                if (userId != null && text != null && linkService.linkFromMessage(userId, text)) {
                    linked++;
                }
            }
            if (linked > 0) log.info("[LINE Webhook] 合言葉で {} 件連携しました", linked);
        } catch (Exception e) {
            // 解析失敗でも LINE には 200 を返す（再送ループ防止）。内容はログで確認。
            log.warn("[LINE Webhook] 解析に失敗: {}", e.toString());
        }
        return ResponseEntity.ok("OK");
    }
}
