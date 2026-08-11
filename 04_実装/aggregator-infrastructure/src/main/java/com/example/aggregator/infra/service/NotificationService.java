package com.example.aggregator.infra.service;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.ArticleNotificationEntity;
import com.example.aggregator.domain.model.NotificationLogEntity;
import com.example.aggregator.domain.model.NotifyResult;
import com.example.aggregator.domain.model.NotifyStatus;
import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.domain.notify.LineNotifier;
import com.example.aggregator.domain.notify.NotificationBundle;
import com.example.aggregator.domain.notify.NotificationItem;
import com.example.aggregator.domain.notify.PushOutcome;
import com.example.aggregator.infra.notify.LineProperties;
import com.example.aggregator.infra.persistence.ArticleNotificationRepository;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.NotificationLogRepository;
import com.example.aggregator.infra.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通知サービス（DD-CLS-02・BD-BATCH-N）。未通知抽出 → お気に入り絞込 → 通数ガード → 送信 → 冪等記録。
 *
 * <p><b>通知経路に LLM を一切置かない</b>（LlmStructurer を注入しない・CLAUDE.md §5・DD-DI-06）。
 * <b>障害分離</b>: 利用者単位で try/catch し、1人の失敗が他を止めない（NFR-10・DD-EXC-05）。
 * <b>冪等</b>: 利用者ごとの処理を1トランザクションにまとめ、ArticleNotifications の主キー存在で二重通知を防ぐ（§5）。
 * 「同日2回目以降は通知しない」は、未通知抽出が article_notifications を NOT EXISTS で除外することで担保される。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserRepository users;
    private final ArticleRepository articles;
    private final ArticleNotificationRepository notifications;
    private final NotificationLogRepository logs;
    private final LineNotifier lineNotifier;
    private final NotificationCountService counts;
    private final LineProperties lineProps;
    private final TransactionTemplate tx;

    public NotificationService(UserRepository users,
                               ArticleRepository articles,
                               ArticleNotificationRepository notifications,
                               NotificationLogRepository logs,
                               LineNotifier lineNotifier,
                               NotificationCountService counts,
                               LineProperties lineProps,
                               PlatformTransactionManager txManager) {
        this.users = users;
        this.articles = articles;
        this.notifications = notifications;
        this.logs = logs;
        this.lineNotifier = lineNotifier;
        this.counts = counts;
        this.lineProps = lineProps;
        // TransactionTemplate: 利用者単位でトランザクション境界を作る（run() から notifyUser を自己呼び出しすると
        // @Transactional プロキシを通らず効かないため、明示的にテンプレートで囲む）。
        this.tx = new TransactionTemplate(txManager);
    }

    public record NotificationResult(int usersProcessed, int usersNotified, int articlesNotified, int messagesSent) {}

    /** 通知バッチ本体。通知可能な利用者ごとに独立処理する。 */
    public NotificationResult run() {
        List<UserEntity> targets = users.findByNotifyEnabledTrueAndLineUserIdNotNull();
        int notified = 0, articleTotal = 0, msgs = 0;
        for (UserEntity u : targets) {
            try {
                UserOutcome o = tx.execute(status -> notifyUser(u));
                if (o != null && o.sent) {
                    notified++;
                    articleTotal += o.articleCount;
                    msgs += o.messageCount;
                }
            } catch (RuntimeException e) {
                // 利用者単位で握って継続（障害分離）。文脈（利用者ID）を残す。秘密情報は出さない。
                log.warn("[通知] 利用者 id={} の処理で例外。スキップして継続: {}", u.getId(), e.toString());
            }
        }
        log.info("[通知] 対象={} 通知成功={} 記事計={} 消費通数={}", targets.size(), notified, articleTotal, msgs);
        return new NotificationResult(targets.size(), notified, articleTotal, msgs);
    }

    private record UserOutcome(boolean sent, int articleCount, int messageCount) {}

    /** 利用者1人ぶんの通知処理（1トランザクション内で実行される）。 */
    private UserOutcome notifyUser(UserEntity user) {
        List<ArticleEntity> targets = articles.findUnnotifiedFavorited(
                user.getId(), PageRequest.of(0, NotificationBundle.MAX_BUBBLES));
        if (targets.isEmpty()) {
            return new UserOutcome(false, 0, 0);
        }

        // 通数ガード（無料枠・FR-03-05）: 上限接近なら送らずアプリ内表示に委ねる（未通知のまま残す）。
        if (!counts.canSend()) {
            log.info("[通知] 当月通数が上限接近（{}/{}）。利用者 id={} は送信せずアプリ内表示のみ。",
                    counts.currentMonthCount(), lineProps.effectiveLimit(), user.getId());
            return new UserOutcome(false, targets.size(), 0);
        }

        List<NotificationItem> items = targets.stream()
                .map(a -> new NotificationItem(a.getId(), a.getTitle(), a.getUrl(), a.getSummary(), a.getEventDate()))
                .toList();
        NotificationBundle bundle = NotificationBundle.of(user.getId(), user.getLineUserId(), items);

        PushOutcome outcome = lineNotifier.push(bundle);
        NotifyStatus status = outcome.status();

        if (status.isSuccess()) {
            markArticles(user.getId(), targets, NotifyResult.DELIVERED);
            logs.save(new NotificationLogEntity(user.getId(), targets.size(), outcome.messageCount(), status));
            user.markNotified(Instant.now());
            users.save(user);
            return new UserOutcome(true, targets.size(), outcome.messageCount());
        }

        // 失敗時: 実績ログは残す（分類の可視化・SC-08）。通数は消費しない。
        logs.save(new NotificationLogEntity(user.getId(), targets.size(), 0, status));

        if (status.giveUp()) {
            // 不具合確定（FormatError）等は打ち切り＝GaveUp。以後この記事は再送しない。
            markArticles(user.getId(), targets, NotifyResult.GAVE_UP);
        } else if (status == NotifyStatus.AUTH_FAILED || status == NotifyStatus.BLOCKED) {
            // 認証不備/ブロックは基本「次回再送」。ただし最初の対象化から5日超過の古い記事は打ち切る（外部IF §3.4）。
            Instant cutoff = Instant.now().minus(lineProps.getGiveUpAfterDays(), ChronoUnit.DAYS);
            for (ArticleEntity a : targets) {
                if (a.getCreatedAt() != null && a.getCreatedAt().isBefore(cutoff)) {
                    saveNotification(user.getId(), a.getId(), NotifyResult.GAVE_UP);
                }
            }
        }
        // TempError/RateLimited/Timeout と、5日以内の Auth/Blocked は ArticleNotifications を作らない
        // ＝未通知のまま次回起動で（同一冪等キーで）再送される（BD-BATCH-N-10）。
        return new UserOutcome(false, targets.size(), 0);
    }

    private void markArticles(Long userId, List<ArticleEntity> targets, NotifyResult result) {
        for (ArticleEntity a : targets) {
            saveNotification(userId, a.getId(), result);
        }
    }

    /** 冪等挿入。既に記録があればスキップ（並行実行・再送での主キー衝突を避ける）。 */
    private void saveNotification(Long userId, Long articleId, NotifyResult result) {
        var key = new ArticleNotificationEntity.Key(userId, articleId);
        if (!notifications.existsByKey(key)) {
            notifications.save(new ArticleNotificationEntity(userId, articleId, result));
        }
    }
}
