package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.service.CostService;
import com.example.aggregator.infra.service.NotificationCountService;
import com.example.aggregator.infra.service.NotificationService;
import com.example.aggregator.web.SampleIngestService;
import com.example.aggregator.web.security.AdminOnly;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.PageRequest;

/**
 * 管理ダッシュボード（SC-07・admin 限定）。バッチ手動実行・LINE通数・LLMコスト（月500円ハードキャップの可視化）・
 * 収集結果の確認/削除を1画面に集約する（FR-06-02〜04/06）。<b>通知処理からLLMは呼ばない</b>ため、
 * LLMコストは収集経由でのみ増える。
 */
@Route(value = "admin", layout = MainLayout.class)
@PageTitle("管理ダッシュボード | アグリゲーター")
public class AdminDashboardView extends VerticalLayout implements AdminOnly {

    private final SampleIngestService sampleIngest;
    private final NotificationService notificationService;
    private final NotificationCountService counts;
    private final CostService cost;
    private final ArticleRepository articles;

    private final Span messageQuota = new Span();
    private final Span llmCost = new Span();
    private final Grid<ArticleEntity> recent = new Grid<>(ArticleEntity.class, false);

    public AdminDashboardView(SampleIngestService sampleIngest, NotificationService notificationService,
                              NotificationCountService counts, CostService cost, ArticleRepository articles) {
        this.sampleIngest = sampleIngest;
        this.notificationService = notificationService;
        this.counts = counts;
        this.cost = cost;
        this.articles = articles;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("管理ダッシュボード");
        title.addClassName("view-title");

        add(title, batchSection(), quotaSection(), recentSection());
        refresh();
    }

    // BD-SC-07-01 バッチ手動実行
    private VerticalLayout batchSection() {
        VerticalLayout box = card("バッチ手動実行");
        Button collect = new Button("収集バッチ実行", e -> {
            var r = sampleIngest.ingestSample();   // 実サイト巡回の本配線までの暫定（同梱サンプルRSSで収集）
            refresh();
            Notification.show("収集: 取得" + r.total() + "件 / 新規" + r.registered() + " / 重複" + r.duplicated());
        });
        collect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button notify = new Button("通知バッチ実行", e -> {
            var r = notificationService.run();
            refresh();
            Notification.show("通知: 対象" + r.usersProcessed() + " / 通知" + r.usersNotified()
                    + " / 通数" + r.messagesSent());
        });
        Span note = new Span("※収集は現在サンプルRSSでの実行です（実サイト巡回の本配線は整備中）。通知はLINE無効時はログのみ。");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        box.add(new HorizontalLayout(collect, notify), note);
        return box;
    }

    // BD-SC-07-02 LINE通数 / BD-SC-07-03 LLM利用状況
    private VerticalLayout quotaSection() {
        VerticalLayout box = card("利用状況（当月・JST）");
        box.add(messageQuota, llmCost);
        return box;
    }

    // BD-SC-07-04 収集結果の確認・削除
    private VerticalLayout recentSection() {
        VerticalLayout box = card("収集結果（直近）");
        recent.addColumn(ArticleEntity::getTitle).setHeader("タイトル").setAutoWidth(true);
        recent.addColumn(a -> a.getEventDate() != null ? a.getEventDate().toString() : "—")
                .setHeader("発生日").setAutoWidth(true);
        recent.addColumn(a -> ThemeView.categoryLabelStatic(a.getCategory())).setHeader("カテゴリ").setAutoWidth(true);
        recent.addColumn(new ComponentRenderer<>(a -> {
            Button del = new Button("削除", e -> {
                articles.deleteById(a.getId());
                refresh();
                Notification.show("削除しました。");
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return del;
        })).setHeader("操作");
        recent.setWidthFull();
        box.add(recent);
        return box;
    }

    private void refresh() {
        int used = counts.currentMonthCount();
        int remain = counts.remaining();
        messageQuota.setText("LINE通数: 当月 " + used + " 通 / 残り約 " + remain + " 通（無料枠200通）");
        messageQuota.getStyle().set("font-weight", "600")
                .set("color", counts.canSend() ? "#4e7d55" : "var(--lumo-error-text-color)");

        CostService.CostSummary c = cost.currentMonth();
        llmCost.setText("LLM: 呼出 " + c.callCount() + "回 / 入力 " + c.inputTokens() + "tok / 出力 "
                + c.outputTokens() + "tok / 概算 " + c.costYen() + "円（上限" + c.budgetJpy() + "円・残 "
                + c.remainingYen() + "円）" + (c.capReached() ? " ⚠ 上限到達: LLM構造化停止中（RSSで収集継続）" : ""));
        llmCost.getStyle().set("font-weight", "600")
                .set("color", c.capReached() ? "var(--lumo-error-text-color)" : "var(--lumo-body-text-color)");

        recent.setItems(articles.findTimeline(PageRequest.of(0, 50)));
    }

    private VerticalLayout card(String heading) {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(true);
        box.setSpacing(true);
        box.setWidthFull();
        box.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px").set("background", "var(--lumo-base-color)");
        H3 h = new H3(heading);
        h.getStyle().set("margin", "0 0 4px 0");
        box.addComponentAsFirst(h);
        return box;
    }
}
