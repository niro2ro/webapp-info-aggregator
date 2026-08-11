package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.infra.persistence.ArticleQuery;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.service.ArticleInteractionService;
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

    private final ArticleRepository articles;
    private final SampleIngestService sampleIngest;
    private final ArticleInteractionService interaction;

    private final TextField search = new TextField();
    private final ComboBox<Category> categoryFilter = new ComboBox<>();
    private final Select<ArticleQuery.Sort> sortSelect = new Select<>();
    private final Checkbox unreadOnly = new Checkbox("未読のみ");
    private final VerticalLayout list = new VerticalLayout();

    public TimelineView(ArticleRepository articles, SampleIngestService sampleIngest,
                        ArticleInteractionService interaction) {
        this.articles = articles;
        this.sampleIngest = sampleIngest;
        this.interaction = interaction;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("タイムライン");
        title.addClassName("view-title");

        // --- ツールバー（並び順・検索） ---
        search.setPlaceholder("検索（タイトル・要約）");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> refresh());

        categoryFilter.setPlaceholder("カテゴリ：すべて");
        categoryFilter.setItems(Category.values());
        categoryFilter.setItemLabelGenerator(ThemeView::categoryLabelStatic);
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.addValueChangeListener(e -> refresh());

        sortSelect.setLabel(null);
        sortSelect.setItems(ArticleQuery.Sort.values());
        sortSelect.setItemLabelGenerator(this::sortLabel);
        sortSelect.setValue(ArticleQuery.Sort.EVENT_DESC);
        sortSelect.addValueChangeListener(e -> refresh());

        unreadOnly.addValueChangeListener(e -> refresh());

        HorizontalLayout toolbar = new HorizontalLayout(sortSelect, categoryFilter, unreadOnly, search);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(search);

        Button collect = new Button("サンプルRSSを収集", e -> {
            sampleIngest.ensureSampleTheme();
            var r = sampleIngest.ingestSample();
            Notification.show("収集: 取得 " + r.total() + " / 新規 " + r.registered() + " / 重複 " + r.duplicated());
            refresh();
        });
        collect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        list.setPadding(false);
        list.setSpacing(true);
        list.setWidthFull();

        add(title, collect, toolbar, list);
        refresh();
    }

    private void refresh() {
        list.removeAll();
        ArticleQuery q = new ArticleQuery(
                USER_ID,
                search.getValue(),
                categoryFilter.getValue(),
                null,
                null,
                unreadOnly.getValue(),
                sortSelect.getValue() == null ? ArticleQuery.Sort.EVENT_DESC : sortSelect.getValue(),
                50);
        List<ArticleEntity> rows = articles.search(q);
        Set<Long> readIds = interaction.readArticleIds(USER_ID);
        Set<Long> bmIds = interaction.bookmarkedArticleIds(USER_ID);
        if (rows.isEmpty()) {
            Span empty = new Span("該当する記事はありません。上の「サンプルRSSを収集」で取り込むか、条件を変えてください。");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            list.add(empty);
            return;
        }
        for (ArticleEntity a : rows) {
            list.add(card(a, readIds.contains(a.getId()), bmIds.contains(a.getId())));
        }
    }

    private Div card(ArticleEntity a, boolean read, boolean bookmarked) {
        String kind = a.getEventDateKind().label();
        String date = a.getEventDate() != null ? a.getEventDate().toString() : "(日付不明)";
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

        Span source = new Span("🌐 情報源#" + a.getSourceId());
        source.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        HorizontalLayout meta = new HorizontalLayout(source, link, readBtn, bm);
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
            case EVENT_DESC -> "発生日順（新しい順）";
            case EVENT_ASC -> "発生日順（古い順）";
            case RELEASE -> "発売日順";
            case COLLECTED_DESC -> "収集日順";
        };
    }
}
