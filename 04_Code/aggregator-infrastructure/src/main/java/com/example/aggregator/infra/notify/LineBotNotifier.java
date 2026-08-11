package com.example.aggregator.infra.notify;

import com.example.aggregator.domain.model.NotifyStatus;
import com.example.aggregator.domain.notify.LineNotifier;
import com.example.aggregator.domain.notify.NotificationBundle;
import com.example.aggregator.domain.notify.NotificationItem;
import com.example.aggregator.domain.notify.PushOutcome;
import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.client.MessagingApiClientException;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LINE Messaging API による push（DD-CLS-17・外部IF §3）。<b>{@code app.line.enabled=true} のときだけ</b> Bean 化。
 * チャネルアクセストークンは {@link LineProperties}（環境変数由来）から受け取る（コード直書き禁止・BD-IF-00-01）。
 *
 * <p><b>冪等キー</b>: {@code pushMessage(UUID, ...)} の第1引数がリトライキー（X-Line-Retry-Key 相当）。
 * {@link NotificationBundle#retryKey()}（利用者×記事集合で決まる決定的 UUID）を渡すことで、
 * 「届いたか不明」な再送での二重配信を防ぐ（BD-IF-03-04/05・NFR-07）。
 *
 * <p><b>1吹き出しに集約</b>: 複数記事を1つの {@link TextMessage} にまとめて送る（＝1通・BD-IF-03-01）。
 * Flex カルーセル（見た目のリッチ化）は UX 改善として後続に回すが、「通数=吹き出し数=1」の無料枠制約は本実装で満たす。
 * 送信結果は例外を投げず {@link PushOutcome} に分類して返す（外部IF §3.4）。
 */
@Component
@ConditionalOnProperty(prefix = "app.line", name = "enabled", havingValue = "true")
public class LineBotNotifier implements LineNotifier {

    private static final Logger log = LoggerFactory.getLogger(LineBotNotifier.class);

    private final MessagingApiClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public LineBotNotifier(LineProperties props) {
        // builder(token): トークンで認証済みクライアントを構築（トークンは環境変数由来）。
        this.client = MessagingApiClient.builder(props.getChannelToken()).build();
        log.info("[LINE] LineBotNotifier 有効化");
    }

    /** テスト用: モックのクライアントを注入する。 */
    LineBotNotifier(MessagingApiClient client) {
        this.client = client;
    }

    @Override
    public PushOutcome push(NotificationBundle bundle) {
        List<Message> messages = List.of(new TextMessage(buildText(bundle)));
        PushMessageRequest request = new PushMessageRequest(bundle.lineUserId(), messages, false, null);
        try {
            // 冪等キー付きで送信し、完了を待つ（バッチはワンショットのため同期でよい）。
            client.pushMessage(bundle.retryKey(), request).get();
            return PushOutcome.success();
        } catch (ExecutionException e) {
            return classify(e.getCause(), bundle);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LINE] 送信が中断 user={}（Timeout 扱いで冪等キー再送）", bundle.userId());
            return PushOutcome.failed(NotifyStatus.TIMEOUT);
        }
    }

    /** 送信本文（1吹き出し）。記事本文は載せず、タイトル・代表日・元URLのみ（権利配慮・§9）。 */
    private static String buildText(NotificationBundle bundle) {
        StringBuilder sb = new StringBuilder("【新着 ").append(bundle.articleCount()).append("件】\n");
        for (NotificationItem it : bundle.items()) {
            sb.append("・").append(it.title());
            if (it.eventDate() != null) sb.append("（").append(it.eventDate()).append("）");
            sb.append("\n").append(it.url()).append("\n");
        }
        return sb.toString().trim();
    }

    /** HTTP ステータス／例外を NotifyStatus に分類する（外部IF §3.4・例外/リトライ §4）。 */
    private PushOutcome classify(Throwable cause, NotificationBundle bundle) {
        if (cause instanceof MessagingApiClientException ex) {
            int code = ex.getCode();
            NotifyStatus status = classifyHttpCode(code);
            log.warn("[LINE] 送信失敗 user={} code={} → {}", bundle.userId(), code, status);
            return PushOutcome.failed(status);
        }
        // コードを伴わない障害（接続断・タイムアウト等）は一時障害／不明として次回再送。
        log.warn("[LINE] 送信失敗 user={}（接続/不明）: {}", bundle.userId(), cause == null ? "null" : cause.toString());
        return PushOutcome.failed(NotifyStatus.TEMP_ERROR);
    }

    /**
     * LINE のHTTPステータス → NotifyStatus（外部IF §3.4 の対応表）。分類規則のみを切り出し単体テスト可能にする。
     * 401=認証不備 / 403=ブロック・未追加 / 429=レート制限 / 400=形式不正（打ち切り） / 5xx=一時障害 / その他=不明扱い。
     */
    static NotifyStatus classifyHttpCode(int code) {
        return switch (code) {
            case 401 -> NotifyStatus.AUTH_FAILED;
            case 403 -> NotifyStatus.BLOCKED;
            case 429 -> NotifyStatus.RATE_LIMITED;
            case 400 -> NotifyStatus.FORMAT_ERROR;
            default -> code >= 500 ? NotifyStatus.TEMP_ERROR : NotifyStatus.BLOCKED;
        };
    }
}
