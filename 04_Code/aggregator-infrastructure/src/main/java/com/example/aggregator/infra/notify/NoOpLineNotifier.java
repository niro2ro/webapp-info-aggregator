package com.example.aggregator.infra.notify;

import com.example.aggregator.domain.model.NotifyStatus;
import com.example.aggregator.domain.notify.LineNotifier;
import com.example.aggregator.domain.notify.NotificationBundle;
import com.example.aggregator.domain.notify.PushOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LINE 無効時の既定実装（{@code app.line.enabled=false} または未設定）。実際には送信せず、送信予定を
 * ログに出して <b>Success 扱い</b>で返す（＝トークン無しでも通知フロー全体を通しで検証できる）。
 *
 * <p>Success を返すため、開発時にこのバッチを回すと ArticleNotifications=Delivered が記録され、以後その記事は
 * 再通知されない（＝冪等の挙動も確認できる）。本番でトークンを入れて有効化すると実送信に切り替わる。
 */
@Component
@ConditionalOnProperty(prefix = "app.line", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpLineNotifier implements LineNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoOpLineNotifier.class);

    @Override
    public PushOutcome push(NotificationBundle bundle) {
        log.info("[LINE:NoOp] 送信スキップ user={} 記事{}件 retryKey={}（トークン未設定。実送信は app.line.enabled=true で有効化）",
                bundle.userId(), bundle.articleCount(), bundle.retryKey());
        return new PushOutcome(NotifyStatus.SUCCESS, 1);
    }
}
