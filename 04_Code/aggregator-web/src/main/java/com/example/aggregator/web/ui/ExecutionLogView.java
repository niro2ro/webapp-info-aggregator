package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.CrawlLogEntity;
import com.example.aggregator.domain.model.CrawlStatus;
import com.example.aggregator.domain.model.NotificationLogEntity;
import com.example.aggregator.domain.model.NotifyStatus;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.infra.persistence.CrawlLogRepository;
import com.example.aggregator.infra.persistence.NotificationLogRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.service.UserService;
import com.example.aggregator.web.security.AdminOnly;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 実行ログ（SC-08・admin 限定）。収集ログ・通知ログをタブで一覧する（FR-06-05）。日時は JST 表示。
 * 通知の失敗行は分類（NotifyStatus）を日本語で示し、対応が要るもの（トークン無効・不具合）を把握できるようにする。
 */
@Route(value = "logs", layout = MainLayout.class)
@PageTitle("実行ログ | アグリゲーター")
public class ExecutionLogView extends VerticalLayout implements AdminOnly {

    private static final java.time.ZoneId JST = java.time.ZoneId.of("Asia/Tokyo");

    private final CrawlLogRepository crawlLogs;
    private final NotificationLogRepository notificationLogs;
    private final SourceRepository sources;
    private final UserService users;

    public ExecutionLogView(CrawlLogRepository crawlLogs, NotificationLogRepository notificationLogs,
                            SourceRepository sources, UserService users) {
        this.crawlLogs = crawlLogs;
        this.notificationLogs = notificationLogs;
        this.sources = sources;
        this.users = users;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("実行ログ");
        title.addClassName("view-title");

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("収集ログ", crawlGrid());
        tabs.add("通知ログ", notifyGrid());

        add(title, tabs);
    }

    private Grid<CrawlLogEntity> crawlGrid() {
        Map<Long, String> sourceName = sources.findAll().stream()
                .collect(Collectors.toMap(SourceEntity::getId, SourceEntity::getName));
        Grid<CrawlLogEntity> g = new Grid<>(CrawlLogEntity.class, false);
        g.addColumn(c -> sourceName.getOrDefault(c.getSourceId(), "(不明)")).setHeader("情報源").setAutoWidth(true);
        g.addColumn(c -> jst(c.getStartedAt())).setHeader("開始(JST)").setAutoWidth(true);
        g.addColumn(c -> jst(c.getFinishedAt())).setHeader("終了(JST)").setAutoWidth(true);
        g.addColumn(CrawlLogEntity::getItemCount).setHeader("取得").setAutoWidth(true);
        g.addColumn(CrawlLogEntity::getNewItemCount).setHeader("新規").setAutoWidth(true);
        g.addColumn(c -> crawlStatusLabel(c.getStatus())).setHeader("状態").setAutoWidth(true);
        g.addColumn(CrawlLogEntity::getErrorMessage).setHeader("エラー").setAutoWidth(true);
        g.setItems(crawlLogs.findTop100ByOrderByStartedAtDesc());
        g.setWidthFull();
        return g;
    }

    private Grid<NotificationLogEntity> notifyGrid() {
        Map<Long, String> userName = users.all().stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getDisplayName));
        Grid<NotificationLogEntity> g = new Grid<>(NotificationLogEntity.class, false);
        g.addColumn(n -> userName.getOrDefault(n.getUserId(), "(不明)")).setHeader("送信先").setAutoWidth(true);
        g.addColumn(n -> jst(n.getSentAt())).setHeader("送信日時(JST)").setAutoWidth(true);
        g.addColumn(NotificationLogEntity::getArticleCount).setHeader("記事件数").setAutoWidth(true);
        g.addColumn(NotificationLogEntity::getMessageCount).setHeader("消費通数").setAutoWidth(true);
        g.addColumn(n -> notifyStatusLabel(n.getStatus())).setHeader("送信結果").setAutoWidth(true);
        g.setItems(notificationLogs.findTop100ByOrderBySentAtDesc());
        g.setWidthFull();
        return g;
    }

    private static String jst(java.time.Instant t) {
        return t == null ? "—" : ZonedDateTime.ofInstant(t, JST).toLocalDateTime().toString();
    }

    private static String crawlStatusLabel(CrawlStatus s) {
        return switch (s) {
            case SUCCESS -> "成功";
            case PARTIAL_ERROR -> "一部失敗";
            case FAILED -> "失敗";
        };
    }

    private static String notifyStatusLabel(NotifyStatus s) {
        return switch (s) {
            case SUCCESS -> "成功";
            case TEMP_ERROR -> "一時障害";
            case RATE_LIMITED -> "レート制限";
            case AUTH_FAILED -> "認証失敗(要トークン確認)";
            case BLOCKED -> "未追加/ブロック";
            case FORMAT_ERROR -> "形式エラー(不具合)";
            case TIMEOUT -> "タイムアウト";
            case GAVE_UP -> "打ち切り";
        };
    }
}
