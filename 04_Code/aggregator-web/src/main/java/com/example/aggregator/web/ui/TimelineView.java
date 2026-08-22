package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.domain.rule.TimeZones;
import com.example.aggregator.infra.persistence.ArticleQuery;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.service.ArticleInteractionService;
import com.example.aggregator.infra.service.ArticleReanalyzeService;
import com.example.aggregator.infra.service.ThemeSearchCollector;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.aggregator.web.SampleIngestService;
import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import java.util.Set;

/**
 * タイムライン（SC-02）。カード型・発生日順（BD-SC-00-02/07）。画像は権利配慮で非表示（§9）。
 * Phase 3: 検索(pg_trgm)・カテゴリ/未読フィルタ・並び順・既読/ブックマークを追加（FR-04-02/03/04・FR-05-03）。
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("タイムライン | アグリゲーター")
public class TimelineView extends VerticalLayout {

    // ログイン中の利用者（Phase 5・認証導入）。ガード未通過の一時生成に備え、未ログイン時は -1（該当データ無し）。
    private final Long USER_ID = CurrentUser.get().map(CurrentUser.Info::id).orElse(-1L);

    /** 1ページの表示件数。小さくして1回あたりの取得・（発売日順の）LLM補完を軽くする。 */
    private static final int PAGE_SIZE = 6;
    /** 現在ページ（0始まり）。 */
    private int page = 0;

    private final ArticleRepository articles;
    private final SampleIngestService sampleIngest;
    private final ArticleInteractionService interaction;
    private final ThemeRepository themes;
    private final ThemeSearchCollector themeSearch;
    private final SourceRepository sources;
    private final ArticleReanalyzeService reanalyze;

    private final TextField search = new TextField();
    private final ComboBox<Category> categoryFilter = new ComboBox<>();
    private final ComboBox<ThemeEntity> themeFilter = new ComboBox<>();
    private final ComboBox<SourceEntity> sourceFilter = new ComboBox<>();
    private final Select<ArticleQuery.Sort> sortSelect = new Select<>();
    private final Checkbox unreadOnly = new Checkbox("未読のみ");
    private final VerticalLayout list = new VerticalLayout();
    private final HorizontalLayout pager = new HorizontalLayout();   // ページ切替（1,2,3…）
    private Map<Long, String> sourceNames = Map.of();   // source_id → 名前（カード表示用）

    public TimelineView(ArticleRepository articles, SampleIngestService sampleIngest,
                        ArticleInteractionService interaction, ThemeRepository themes,
                        ThemeSearchCollector themeSearch, SourceRepository sources,
                        ArticleReanalyzeService reanalyze) {
        this.articles = articles;
        this.sampleIngest = sampleIngest;
        this.interaction = interaction;
        this.themes = themes;
        this.themeSearch = themeSearch;
        this.sources = sources;
        this.reanalyze = reanalyze;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("タイムライン");
        title.addClassName("view-title");

        // --- ツールバー（並び順・検索） ---
        search.setPlaceholder("検索（タイトル・要約）");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> resetPageAndRefresh());

        categoryFilter.setPlaceholder("カテゴリ：すべて");
        categoryFilter.setItems(Category.values());
        categoryFilter.setItemLabelGenerator(ThemeView::categoryLabelStatic);
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.addValueChangeListener(e -> resetPageAndRefresh());

        // テーマ絞り込み（登録テーマにマッチした記事のみ表示・BD-SC-02-05）
        themeFilter.setPlaceholder("テーマ：すべて");
        themeFilter.setItemLabelGenerator(ThemeEntity::getKeyword);
        themeFilter.setClearButtonVisible(true);
        themeFilter.addValueChangeListener(e -> resetPageAndRefresh());
        reloadThemeItems();

        // 情報源で絞り込み（統合タイムラインのまま、RSS/検索など由来を切り替えられる）
        sourceFilter.setPlaceholder("情報源：すべて");
        sourceFilter.setItemLabelGenerator(SourceEntity::getName);
        sourceFilter.setClearButtonVisible(true);
        sourceFilter.addValueChangeListener(e -> resetPageAndRefresh());
        reloadSourceItems();

        sortSelect.setLabel(null);
        sortSelect.setItems(ArticleQuery.Sort.values());
        sortSelect.setItemLabelGenerator(this::sortLabel);
        sortSelect.setValue(ArticleQuery.Sort.PUBLISHED_DESC);   // 既定は掲載日順（RSSで確実・LLM不要）
        sortSelect.addValueChangeListener(e -> onSortChanged(e.getValue()));

        unreadOnly.addValueChangeListener(e -> resetPageAndRefresh());

        HorizontalLayout toolbar = new HorizontalLayout(sortSelect, themeFilter, sourceFilter, categoryFilter, unreadOnly, search);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(search);

        // 登録テーマのキーワードで検索RSSから収集（テーマ語で最近の記事を探しに行く）
        Button collectThemes = new Button("登録テーマの記事を収集", e -> {
            var r = themeSearch.collectForUser(USER_ID);
            if (r.themes() == 0) {
                Notification.show("有効なテーマがありません。「テーマ管理」で追加してください。", 4000, Notification.Position.MIDDLE);
            } else {
                Notification.show("テーマ検索: テーマ" + r.themes() + " / 取得" + r.totalFetched()
                        + " / 新規" + r.totalRegistered() + (r.failed() > 0 ? " / 失敗" + r.failed() : ""));
            }
            reloadThemeItems();
            refresh();
        });
        collectThemes.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button collect = new Button("サンプルRSSを収集", e -> {
            sampleIngest.ensureSampleTheme();
            var r = sampleIngest.ingestSample();
            Notification.show("収集: 取得 " + r.total() + " / 新規 " + r.registered() + " / 重複 " + r.duplicated());
            reloadThemeItems();
            refresh();
        });
        collect.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        HorizontalLayout actions = new HorizontalLayout(collectThemes, collect);

        list.setPadding(false);
        list.setSpacing(true);
        list.setWidthFull();

        pager.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        pager.setWidthFull();
        pager.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        add(title, actions, toolbar, list, pager);
        refresh();
    }

    private void resetPageAndRefresh() {
        page = 0;
        refresh();
    }

    private void goToPage(int p) {
        page = Math.max(0, p);
        // 発売日順のときは、このページに出す自分のテーマ記事の発売日を（未設定分だけ）補完してから表示。
        maybeFillReleaseDates(sortSelect.getValue());
        refresh();
    }

    /** テーマ絞り込みドロップダウンの選択肢を、ログイン利用者の有効テーマで更新する。 */
    private void reloadThemeItems() {
        themeFilter.setItems(themes.findByUserIdAndActiveTrueOrderByKeyword(USER_ID));
    }

    /** 情報源フィルタの選択肢と、カード表示用の source_id→名前 マップを更新する。 */
    private void reloadSourceItems() {
        List<SourceEntity> all = sources.findAll();
        sourceFilter.setItems(all);
        sourceNames = all.stream().collect(Collectors.toMap(SourceEntity::getId, SourceEntity::getName));
    }

    /** 並び順が変わったとき。ページを先頭へ戻し、発売日順ならこのページ分の発売日を補完してから表示。 */
    private void onSortChanged(ArticleQuery.Sort sort) {
        page = 0;
        maybeFillReleaseDates(sort);
        refresh();
    }

    /**
     * 「発売日順」のときだけ、自分のテーマ記事で発売日が未設定のものを1ページぶん(PAGE_SIZE)を上限に LLM 補完する
     * （掲載日順など他の並びは LLM を呼ばず即時）。上限で切るため1回で全ては埋まらない＝ページ送り/再選択で続きを補完。
     * 処理中は Vaadin の読み込みインジケータが出る。
     */
    private void maybeFillReleaseDates(ArticleQuery.Sort sort) {
        if (sort != ArticleQuery.Sort.RELEASE_ASC && sort != ArticleQuery.Sort.RELEASE_DESC) return;
        // まずルールベースで（無料・即時）、取れない曖昧なものだけ LLM（有効時）で補完する。
        ArticleReanalyzeService.Result r = reanalyze.reanalyzeForUserThemes(USER_ID, PAGE_SIZE);
        if (r.updated() > 0) {
            Notification.show("発売日を " + r.updated() + " 件補完しました。（未設定が残る場合はページ送り等で続きを補完します）");
        } else if (!r.llmEnabled() && r.scanned() > 0) {
            Notification.show("ルールで発売日を特定できない記事が残っています。"
                    + "LLMを有効化すると本文から抽出できます（LLM_ENABLED=true と ANTHROPIC_API_KEY）。",
                    5000, Notification.Position.MIDDLE);
        }
    }

    /** 現在の絞り込み・並び順・ページ位置から検索条件を組み立てる。 */
    private ArticleQuery query(ArticleQuery.Sort sort, int offset) {
        return new ArticleQuery(
                USER_ID,
                search.getValue(),
                categoryFilter.getValue(),
                null,   // 発生日種別（未使用）
                themeFilter.getValue() == null ? null : themeFilter.getValue().getId(),
                sourceFilter.getValue() == null ? null : sourceFilter.getValue().getId(),
                unreadOnly.getValue(),
                sort,
                PAGE_SIZE,
                offset);
    }

    private void refresh() {
        list.removeAll();
        ArticleQuery.Sort sort = sortSelect.getValue() == null ? ArticleQuery.Sort.PUBLISHED_DESC : sortSelect.getValue();

        long total = articles.count(query(sort, 0));
        int totalPages = (int) Math.max(1, Math.ceil(total / (double) PAGE_SIZE));
        if (page > totalPages - 1) page = totalPages - 1;   // 絞り込みで減ったとき末尾に丸める
        if (page < 0) page = 0;

        List<ArticleEntity> rows = articles.search(query(sort, page * PAGE_SIZE));
        Set<Long> readIds = interaction.readArticleIds(USER_ID);
        Set<Long> bmIds = interaction.bookmarkedArticleIds(USER_ID);
        if (rows.isEmpty()) {
            Span empty = new Span("該当する記事はありません。「テーマ管理」でテーマを追加し、上の"
                    + "「登録テーマの記事を収集」を押すと、そのテーマの最近の記事を集めます（条件を変えても確認できます）。");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            list.add(empty);
            renderPager(total);
            return;
        }
        for (ArticleEntity a : rows) {
            list.add(card(a, readIds.contains(a.getId()), bmIds.contains(a.getId()), sort));
        }
        renderPager(total);
    }

    /** ページ切替（前へ / 1 2 3 … / 次へ）。総件数からページ数を出し、現在ページ中心に最大5個の番号を出す。 */
    private void renderPager(long total) {
        pager.removeAll();
        int totalPages = (int) Math.max(1, Math.ceil(total / (double) PAGE_SIZE));
        if (totalPages <= 1) { pager.setVisible(false); return; }
        pager.setVisible(true);

        Button first = new Button("« 最初へ", e -> goToPage(0));
        first.setEnabled(page > 0);
        first.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        pager.add(first);

        Button prev = new Button("前へ", e -> goToPage(page - 1));
        prev.setEnabled(page > 0);
        prev.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        pager.add(prev);

        int to = Math.min(totalPages - 1, Math.max(page + 2, 4));
        int from = Math.max(0, to - 4);
        for (int i = from; i <= to; i++) {
            int p = i;
            Button b = new Button(String.valueOf(i + 1), e -> goToPage(p));
            b.addThemeVariants(i == page ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL);
            pager.add(b);
        }

        Button next = new Button("次へ", e -> goToPage(page + 1));
        next.setEnabled(page < totalPages - 1);
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        pager.add(next);

        Button last = new Button("最後へ »", e -> goToPage(totalPages - 1));
        last.setEnabled(page < totalPages - 1);
        last.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        pager.add(last);

        Span info = new Span("全 " + total + " 件 / " + (page + 1) + " / " + totalPages + " ページ");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px")
                .set("margin-left", "8px");
        pager.add(info);
    }

    private Div card(ArticleEntity a, boolean read, boolean bookmarked, ArticleQuery.Sort sort) {
        // 表示する日付は「並び順に一致」させる（混在させない）:
        //  ・発売日順 → 発売日(event_date)のみ（未設定は「未定」）
        //  ・掲載日順 → 掲載日(published_at)のみ
        //  ・収集日順 → 収集日(created_at)のみ
        String kind;
        String date;
        switch (sort) {
            case RELEASE_ASC, RELEASE_DESC -> {
                kind = "発売日";
                if (a.getEventDate() != null) {
                    String k = a.getEventDateKind() == null ? null : a.getEventDateKind().label();
                    if (k != null && !k.isEmpty()) kind = k;   // 発売日/開催日/放送日 等
                    date = a.getEventDate().toString();
                } else {
                    date = "未定";
                }
            }
            case COLLECTED_DESC -> {
                kind = "収集日";
                date = a.getCreatedAt() != null
                        ? a.getCreatedAt().atZone(TimeZones.JST).toLocalDate().toString() : "-";
            }
            default -> {   // PUBLISHED_DESC（掲載日順）
                kind = "掲載日";
                date = a.getPublishedAt() != null
                        ? a.getPublishedAt().atZone(TimeZones.JST).toLocalDate().toString() : "-";
            }
        }
        Span kindSpan = new Span(kind.isEmpty() ? " " : kind);
        kindSpan.getStyle().set("display", "block").set("font-size", "11px")
                .set("color", "var(--lumo-secondary-text-color)");
        Span dateSpan = new Span(date);
        dateSpan.addClassName("art-date");
        dateSpan.getStyle().set("display", "block");
        Div dateBox = new Div(kindSpan, dateSpan);
        dateBox.getStyle().set("flex", "0 0 120px");

        Div noimg = new Div(new Text("画像非表示"));
        noimg.addClassName("art-noimg");

        Span cat = new Span(ThemeView.categoryLabelStatic(a.getCategory()));
        cat.getElement().getThemeList().add("badge");
        // 未読は太字＋ドット
        Span titleSpan = new Span((read ? "" : "● ") + a.getTitle());
        titleSpan.addClassName("art-title");
        if (!read) {
            titleSpan.getStyle().set("color", "var(--lumo-primary-text-color)");
        } else {
            titleSpan.getStyle().set("font-weight", "500")
                    .set("color", "var(--lumo-secondary-text-color)");
        }
        Div row1 = new Div(cat, titleSpan);
        row1.getStyle().set("display", "flex").set("gap", "8px").set("align-items", "center")
                .set("flex-wrap", "wrap");

        Span summary = new Span(a.getSummary() == null ? "" : a.getSummary());
        summary.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "13px")
                .set("display", "block").set("margin", "4px 0");

        Anchor link = new Anchor(a.getUrl(), "元記事を開く ↗");
        link.setTarget("_blank");
        link.getElement().setAttribute("rel", "noopener noreferrer");

        Button bm = new Button(bookmarked ? "★ 保存済" : "☆ 後で見る", e -> {
            interaction.toggleBookmark(USER_ID, a.getId());
            refresh();
        });
        bm.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button readBtn = new Button(read ? "既読" : "既読にする", e -> {
            interaction.markRead(USER_ID, a.getId());
            refresh();
        });
        readBtn.setEnabled(!read);
        readBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        // 由来バッジ＋情報源名（検索由来か専門サイト由来かを一目で）
        String srcName = sourceNames.getOrDefault(a.getSourceId(), "情報源#" + a.getSourceId());
        boolean fromSearch = ThemeSearchCollector.SEARCH_SOURCE_NAME.equals(srcName);
        Span origin = new Span(fromSearch ? "🔎 検索" : "🏷 サイト");
        origin.getElement().getThemeList().add("badge");
        origin.getElement().getThemeList().add(fromSearch ? "contrast" : "success");
        origin.getStyle().set("font-size", "11px");
        Span source = new Span("🌐 " + srcName);
        source.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        HorizontalLayout meta = new HorizontalLayout(origin, source, link, readBtn, bm);
        meta.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        meta.setSpacing(true);

        Div body = new Div(row1, summary, meta);
        body.getStyle().set("flex", "1");

        Div card = new Div(dateBox, noimg, body);
        card.addClassName("art-card");
        card.getStyle().set("display", "flex").set("gap", "14px").set("width", "100%");
        if (!read) {
            card.getStyle().set("border-left", "3px solid var(--lumo-primary-color)");
        }
        return card;
    }

    private String sortLabel(ArticleQuery.Sort s) {
        return switch (s) {
            case RELEASE_ASC -> "発売日順（近い順）";       // 実イベント日が近い順（日付なしは末尾）
            case RELEASE_DESC -> "発売日順（遠い順）";       // 実イベント日が遠い順
            case PUBLISHED_DESC -> "掲載日順";             // 記事が配信された日
            case COLLECTED_DESC -> "収集日順";             // こちらが取り込んだ日
        };
    }
}
