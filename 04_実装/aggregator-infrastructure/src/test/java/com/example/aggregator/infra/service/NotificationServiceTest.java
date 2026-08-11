package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.ArticleNotificationEntity;
import com.example.aggregator.domain.model.NotificationLogEntity;
import com.example.aggregator.domain.model.NotifyResult;
import com.example.aggregator.domain.model.NotifyStatus;
import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.domain.notify.LineNotifier;
import com.example.aggregator.domain.notify.PushOutcome;
import com.example.aggregator.infra.notify.LineProperties;
import com.example.aggregator.infra.persistence.ArticleNotificationRepository;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.NotificationLogRepository;
import com.example.aggregator.infra.persistence.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 通知サービスの分岐（BD-BATCH-N・外部IF §3.4）を検証する。DB/LINE はダブル。
 * トランザクションはモックの {@link PlatformTransactionManager} で、テンプレートがコールバックを同期実行する。
 *
 * <p>{@code MockitoExtension} でテストごとにモックを作り直す（verify の回数がテスト間で累積しないように）。
 * ヘルパ user()/article() は分岐によって使わない getter も stub するため、strictness は LENIENT にする。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock UserRepository users;
    @Mock ArticleRepository articles;
    @Mock ArticleNotificationRepository notifications;
    @Mock NotificationLogRepository logs;
    @Mock LineNotifier lineNotifier;
    @Mock NotificationCountService counts;
    @Mock PlatformTransactionManager txManager;
    private final LineProperties lineProps = new LineProperties();

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(users, articles, notifications, logs,
                lineNotifier, counts, lineProps, txManager);
    }

    private UserEntity user() {
        UserEntity u = mock(UserEntity.class);
        when(u.getId()).thenReturn(2L);
        when(u.getLineUserId()).thenReturn("U123");
        return u;
    }

    /** 最近作成された記事1件（5日ルールに掛からない）。 */
    private ArticleEntity article(long id) {
        ArticleEntity a = mock(ArticleEntity.class);
        when(a.getId()).thenReturn(id);
        when(a.getCreatedAt()).thenReturn(Instant.now());
        return a;
    }

    @Test
    @DisplayName("成功: Delivered 記録・SUCCESS ログ・last_notified 更新・通数1")
    void successPath() {
        // 注意: ネストした when(...).thenReturn(...) 内でモックを生成すると UnfinishedStubbing になるため、
        // モック（内部で when を呼ぶ user()/article()）は事前に組み立ててから stub に渡す。
        UserEntity u = user();
        ArticleEntity a = article(100);
        when(users.findByNotifyEnabledTrueAndLineUserIdNotNull()).thenReturn(List.of(u));
        when(articles.findUnnotifiedFavorited(eq(2L), any())).thenReturn(List.of(a));
        when(counts.canSend()).thenReturn(true);
        when(notifications.existsByKey(any())).thenReturn(false);
        when(lineNotifier.push(any())).thenReturn(PushOutcome.success());

        NotificationService.NotificationResult r = service.run();

        assertThat(r.usersNotified()).isEqualTo(1);
        assertThat(r.messagesSent()).isEqualTo(1);
        ArgumentCaptor<ArticleNotificationEntity> an = ArgumentCaptor.forClass(ArticleNotificationEntity.class);
        verify(notifications).save(an.capture());
        assertThat(an.getValue().getResult()).isEqualTo(NotifyResult.DELIVERED);
        ArgumentCaptor<NotificationLogEntity> lg = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(logs).save(lg.capture());
        assertThat(lg.getValue().getStatus()).isEqualTo(NotifyStatus.SUCCESS);
        verify(users).save(any());
    }

    @Test
    @DisplayName("通数上限接近: 送信せず（push を呼ばない）・通知記録もしない")
    void quotaBlocksSend() {
        UserEntity u = user();
        ArticleEntity a = article(100);
        when(users.findByNotifyEnabledTrueAndLineUserIdNotNull()).thenReturn(List.of(u));
        when(articles.findUnnotifiedFavorited(eq(2L), any())).thenReturn(List.of(a));
        when(counts.canSend()).thenReturn(false);

        NotificationService.NotificationResult r = service.run();

        assertThat(r.usersNotified()).isZero();
        verify(lineNotifier, never()).push(any());
        verify(notifications, never()).save(any());
        verify(logs, never()).save(any());
    }

    @Test
    @DisplayName("FormatError: 打ち切り＝GaveUp 記録・users は更新しない")
    void formatErrorGivesUp() {
        UserEntity u = user();
        ArticleEntity a = article(100);
        when(users.findByNotifyEnabledTrueAndLineUserIdNotNull()).thenReturn(List.of(u));
        when(articles.findUnnotifiedFavorited(eq(2L), any())).thenReturn(List.of(a));
        when(counts.canSend()).thenReturn(true);
        when(notifications.existsByKey(any())).thenReturn(false);
        when(lineNotifier.push(any())).thenReturn(PushOutcome.failed(NotifyStatus.FORMAT_ERROR));

        NotificationService.NotificationResult r = service.run();

        assertThat(r.usersNotified()).isZero();
        ArgumentCaptor<ArticleNotificationEntity> an = ArgumentCaptor.forClass(ArticleNotificationEntity.class);
        verify(notifications).save(an.capture());
        assertThat(an.getValue().getResult()).isEqualTo(NotifyResult.GAVE_UP);
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("TempError: 未通知のまま（ArticleNotifications を作らない＝次回再送）")
    void tempErrorLeavesUnnotified() {
        UserEntity u = user();
        ArticleEntity a = article(100);
        when(users.findByNotifyEnabledTrueAndLineUserIdNotNull()).thenReturn(List.of(u));
        when(articles.findUnnotifiedFavorited(eq(2L), any())).thenReturn(List.of(a));
        when(counts.canSend()).thenReturn(true);
        when(lineNotifier.push(any())).thenReturn(PushOutcome.failed(NotifyStatus.TEMP_ERROR));

        NotificationService.NotificationResult r = service.run();

        assertThat(r.usersNotified()).isZero();
        verify(notifications, never()).save(any());     // 未通知のまま
        ArgumentCaptor<NotificationLogEntity> lg = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(logs).save(lg.capture());               // 実績ログは残す
        assertThat(lg.getValue().getStatus()).isEqualTo(NotifyStatus.TEMP_ERROR);
    }
}
