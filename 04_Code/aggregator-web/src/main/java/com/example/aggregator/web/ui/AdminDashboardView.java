package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.service.ThemeSearchCollector;
import com.example.aggregator.infra.llm.LlmProperties;
import com.example.aggregator.domain.rule.TimeZones;
import com.example.aggregator.infra.service.ArticleReanalyzeService;
import com.example.aggregator.infra.service.CollectionRunner;
import com.example.aggregator.infra.service.CostService;
import com.example.aggregator.infra.service.FeedProbeService;
import com.example.aggregator.infra.service.NotificationCountService;
import com.example.aggregator.infra.service.NotificationService;
import com.example.aggregator.web.security.AdminOnly;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.domain.PageRequest;

/**
 * 管理ダッシュボード（SC-07・admin 限定）。バッチ手動実行・LINE通数・LLMコスト（月500円ハードキャップの可視化）・
 * 収集結果の確認/削除を1画面に集約する（FR-06-02〜04/06）。<b>通知処理からLLMは呼ばない</b>ため、
 * LLMコストは収集経由でのみ増える。
 */
@Route(value = "admin", layout = MainLayout.class)
@PageTitle("管理ダッシュボード | 情報収集ツール")
public class AdminDashboardView extends VerticalLayout implements AdminOnly {

    /** 診断結果の日付表示は JST（保存はUTC・表示のみJST・NFR-08）。 */
    private static final DateTimeFormatter PROBE_DATE =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(TimeZones.JST);

    private final CollectionRunner collectionRunner;
    private final NotificationService notificationService;
    private final NotificationCountService counts;
    private final CostService cost;
    private final ArticleRepository articles;
    private final ArticleReanalyzeService reanalyze;
    private final LlmProperties llmProps;
    private final FeedProbeService feedProbe;
    private final SourceRepository sources;

    private final Span messageQuota = new Span();
    private final Span llmCost = new Span();
    private final Grid<ArticleEntity> recent = new Grid<>(ArticleEntity.class, false);

    public AdminDashboardView(CollectionRunner collectionRunner, NotificationService notificationService,
                              NotificationCountService counts, CostService cost, ArticleRepository articles,
                              ArticleReanalyzeService reanalyze, LlmProperties llmProps,
                              FeedProbeService feedProbe, SourceRepository sources) {
        this.collectionRunner = collectionRunner;
        this.notificationService = notificationService;
        this.counts = counts;
        this.cost = cost;
        this.articles = articles;
        this.reanalyze = reanalyze;
        this.llmProps = llmProps;
        this.feedProbe = feedProbe;
        this.sources = sources;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("管理ダッシュボード");
        title.addClassName("view-title");

        add(title, batchSection(), feedProbeSection(), llmSection(), quotaSection(), recentSection());
        refresh();
    }

    // RSS取得テスト（保存なし）: 任意のフィードURLを実際に叩いて「取得件数・先頭数件」を確認する。
    // 規約確認や情報源登録より前に、そのサイトのRSSが本当に取れるかを切り分けるための道具。
    private VerticalLayout feedProbeSection() {
        VerticalLayout box = card("RSS取得テスト（保存なし・確認用）");

        TextField url = new TextField();
        url.setPlaceholder("https://example.com/rss.xml など、RSS/AtomフィードのURL");
        url.setWidthFull();
        url.setClearButtonVisible(true);

        // 結果表示領域（押すたびに作り直す）。
        VerticalLayout result = new VerticalLayout();
        result.setPadding(false);
        result.setSpacing(false);

        Button probe = new Button("取得テスト", e -> {
            result.removeAll();
            result.add(renderProbe(feedProbe.probe(url.getValue()), true));
        });
        probe.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // 情報源マスタの各URLを1件ずつ個別にテストする（テーマ検索用の内部ソースは対象外）。
        Button probeAll = new Button("情報源マスタを一括テスト（全URL）", e -> {
            result.removeAll();
            List<SourceEntity> targets = sources.findAll().stream()
                    .filter(s -> !ThemeSearchCollector.SEARCH_SOURCE_NAME.equals(s.getName()))
                    .toList();
            if (targets.isEmpty()) {
                Span none = new Span("情報源マスタに対象がありません。");
                none.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "13px");
                result.add(none);
                return;
            }
            int ok = 0;
            List<FeedProbeService.NamedProbe> results = feedProbe.probeSources(targets);
            for (FeedProbeService.NamedProbe np : results) {
                if (np.result().ok()) ok++;
                result.add(renderSourceProbe(np));
            }
            Span summary = new Span("一括テスト完了: " + targets.size() + " 件中 成功 " + ok
                    + " / 失敗 " + (targets.size() - ok) + "（DBには保存していません）");
            summary.getStyle().set("font-weight", "600").set("font-size", "13px").set("margin-top", "4px");
            result.addComponentAsFirst(summary);
        });
        probeAll.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        Span note = new Span("実サイトのRSS/AtomフィードのURLを入れて「取得テスト」を押すと、実際に取得・解析して "
                + "件数と先頭" + FeedProbeService.SAMPLE_LIMIT + "件を表示します。"
                + "「情報源マスタを一括テスト」は登録済み情報源のURLを1件ずつ個別に確認します。"
                + "いずれもDBには保存しません。収集バッチ・テーマ検索収集と同じ取得/解析経路を使うため、"
                + "ここで取れれば本番でも取れます。取れない場合は、そのURLがRSSでない/取得がブロックされている可能性です。");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");

        box.add(url, new HorizontalLayout(probe, probeAll), note, result);
        return box;
    }

    /** 情報源1件ぶんの結果を「名前＋URL＋判定＋サンプル」で描画する（一括テスト用）。 */
    private VerticalLayout renderSourceProbe(FeedProbeService.NamedProbe np) {
        VerticalLayout block = new VerticalLayout();
        block.setPadding(false);
        block.setSpacing(false);
        block.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)").set("padding-top", "6px");
        Span head = new Span((np.result().ok() ? "✓ " : "✕ ") + np.name());
        head.getStyle().set("font-weight", "700").set("font-size", "13px")
                .set("color", np.result().ok() ? "#4e7d55" : "var(--lumo-error-text-color)");
        Span urlLine = new Span(np.url());
        urlLine.getStyle().set("font-size", "11px").set("color", "var(--lumo-secondary-text-color)");
        block.add(head, urlLine, renderProbe(np.result(), false));
        return block;
    }

    /**
     * 診断結果を描画する。{@code withSamples=true} なら先頭数件のタイトル/日付/リンクも出す（単発テスト用）。
     * 一括テストでは件数だけ簡潔に出す（{@code withSamples=false}）。
     */
    private VerticalLayout renderProbe(FeedProbeService.ProbeResult r, boolean withSamples) {
        VerticalLayout out = new VerticalLayout();
        out.setPadding(false);
        out.setSpacing(false);
        if (!r.ok()) {
            Span err = new Span("✕ " + r.error());
            err.getStyle().set("color", "var(--lumo-error-text-color)").set("font-weight", "600").set("font-size", "13px");
            out.add(err);
            return out;
        }
        Span ok = new Span("✓ 取得成功: " + r.count() + " 件");
        ok.getStyle().set("color", "#4e7d55").set("font-weight", "600").set("font-size", "13px");
        out.add(ok);
        if (r.count() == 0) {
            Span empty = new Span("※フィードとしては読めましたが、記事が0件でした（検索語ヒットなし等）。");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
            out.add(empty);
        }
        if (withSamples) {
            for (FeedProbeService.ProbeItem it : r.samples()) {
                String date = it.publishedAt() != null ? PROBE_DATE.format(it.publishedAt()) : "日付なし";
                Span line = new Span("・[" + date + "] " + (it.title() == null ? "(タイトルなし)" : it.title()));
                line.getStyle().set("font-size", "13px");
                VerticalLayout item = new VerticalLayout(line);
                item.setPadding(false);
                item.setSpacing(false);
                if (it.url() != null) {
                    Anchor a = new Anchor(it.url(), it.url());
                    a.setTarget("_blank");
                    a.getStyle().set("font-size", "12px").set("color", "var(--lumo-secondary-text-color)");
                    item.add(a);
                }
                out.add(item);
            }
        } else if (!r.samples().isEmpty()) {
            // 一括テストでは代表として先頭1件のタイトルだけ添える（一覧を短く保つ）。
            FeedProbeService.ProbeItem first = r.samples().get(0);
            Span sample = new Span("例: " + (first.title() == null ? "(タイトルなし)" : first.title()));
            sample.getStyle().set("font-size", "12px").set("color", "var(--lumo-secondary-text-color)");
            out.add(sample);
        }
        return out;
    }

    // LLM 稼働状況＋既存記事の再解析（発売日の穴埋め）
    private VerticalLayout llmSection() {
        VerticalLayout box = card("LLM（発売日の抽出）");

        Span status = new Span();
        if (llmProps.isEnabled()) {
            status.setText("状態: 有効（model=" + llmProps.getModel() + "）— 「発売日順」表示時やこの再解析で本文を読んで発売日を抽出します");
            status.getStyle().set("color", "#4e7d55");
        } else {
            status.setText("状態: 無効（APIキー未設定 → 発売日は抽出されません。環境変数 LLM_ENABLED=true と "
                    + "ANTHROPIC_API_KEY を設定して再起動してください）");
            status.getStyle().set("color", "var(--lumo-error-text-color)");
        }
        status.getStyle().set("font-weight", "600").set("font-size", "13px");

        Button reanalyzeBtn = new Button("発売日を再解析（未設定のみ・最大30件）", e -> {
            var r = reanalyze.reanalyzeMissingEventDates(30);
            refresh();
            if (!r.llmEnabled()) {
                Notification.show("LLMが無効です。LLM_ENABLED=true と ANTHROPIC_API_KEY を設定して再起動してください。",
                        5000, Notification.Position.MIDDLE);
            } else {
                Notification.show("再解析: 対象" + r.scanned() + " / 発売日を更新" + r.updated()
                        + (r.failed() > 0 ? " / 失敗" + r.failed() : ""));
            }
        });
        reanalyzeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        reanalyzeBtn.setEnabled(llmProps.isEnabled());

        Span note = new Span("収集前にLLMが無効だった記事など、発売日が空のものだけを本文から抽出して埋め直します"
                + "（同じ記事の再取得ではDBは変わりません）。予算（月上限）内で動きます。");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");

        box.add(status, reanalyzeBtn, note);
        return box;
    }

    // BD-SC-07-01 バッチ手動実行
    private VerticalLayout batchSection() {
        VerticalLayout box = card("バッチ手動実行");
        Button collect = new Button("収集バッチ実行", e -> {
            var r = collectionRunner.run();   // 規約確認済の情報源から実サイト収集（robots/間隔/UA を遵守）
            refresh();
            if (r.sources() == 0) {
                Notification.show("収集対象0件。「情報源マスタ」で規約確認済（有効かつ規約OK）の情報源を用意してください。",
                        5000, Notification.Position.MIDDLE);
            } else {
                Notification.show("収集: 情報源" + r.sources() + " / 成功" + r.succeeded() + " / 失敗" + r.failed()
                        + " / 取得" + r.totalFetched() + " / 新規" + r.totalRegistered());
            }
        });
        collect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button notify = new Button("通知バッチ実行", e -> {
            var r = notificationService.run();
            refresh();
            Notification.show("通知: 対象" + r.usersProcessed() + " / 通知" + r.usersNotified()
                    + " / 通数" + r.messagesSent());
        });
        Span note = new Span("※収集は規約確認済の情報源から実サイト取得します（robots.txt・同一ホスト1秒間隔・"
                + "連絡先入りUAを遵守）。通知はLINE無効時はログのみ。");
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
