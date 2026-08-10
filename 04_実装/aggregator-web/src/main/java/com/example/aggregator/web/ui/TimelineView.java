package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.web.SampleIngestService;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import org.springframework.data.domain.PageRequest;

/**
 * タイムライン（SC-02）。記事をカード型で発生日順に表示（BD-SC-00-02）。画像は権利配慮で非表示（§9）。
 * Phase 1 はサンプル RSS の収集ボタン付き（実サイト収集は Phase 1/2 で本実装）。
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("タイムライン | アグリゲーター")
public class TimelineView extends VerticalLayout {

    private final ArticleRepository articles;
    private final SampleIngestService sampleIngest;
    private final VerticalLayout list = new VerticalLayout();

    public TimelineView(ArticleRepository articles, SampleIngestService sampleIngest) {
        this.articles = articles;
        this.sampleIngest = sampleIngest;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("タイムライン");
        title.addClassName("view-title");

        Button collect = new Button("サンプルRSSを収集", e -> {
            sampleIngest.ensureSampleTheme();
            var r = sampleIngest.ingestSample();
            Notification.show("収集: 取得 " + r.total() + " / 新規 " + r.registered() + " / 重複 " + r.duplicated());
            refresh();
        });
        collect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Span note = new Span("※Phase 1 デモ: バンドルしたサンプルRSSを取り込みます（実サイトへは接続しません）");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)");
        note.getStyle().set("font-size", "12px");

        HorizontalLayout toolbar = new HorizontalLayout(collect, note);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        list.setPadding(false);
        list.setSpacing(true);
        list.setWidthFull();

        add(title, toolbar, list);
        refresh();
    }

    private void refresh() {
        list.removeAll();
        List<ArticleEntity> rows = articles.findTimeline(PageRequest.of(0, 30));
        if (rows.isEmpty()) {
            Span empty = new Span("該当する記事はありません。上の「サンプルRSSを収集」で取り込めます。");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            list.add(empty);
            return;
        }
        for (ArticleEntity a : rows) {
            list.add(card(a));
        }
    }

    private Div card(ArticleEntity a) {
        String kind = a.getEventDateKind().label();
        String date = a.getEventDate() != null ? a.getEventDate().toString() : "(日付不明)";

        Span kindSpan = new Span(kind.isEmpty() ? " " : kind);
        kindSpan.getStyle().set("display", "block");
        kindSpan.getStyle().set("font-size", "11px");
        kindSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Span dateSpan = new Span(date);
        dateSpan.addClassName("art-date");
        dateSpan.getStyle().set("display", "block");
        Div dateBox = new Div(kindSpan, dateSpan);
        dateBox.getStyle().set("flex", "0 0 120px");

        Div noimg = new Div(new Text("画像非表示"));
        noimg.addClassName("art-noimg");

        Span cat = new Span(categoryLabel(a.getCategory()));
        cat.getElement().getThemeList().add("badge");
        Span titleSpan = new Span(a.getTitle());
        titleSpan.addClassName("art-title");
        Div row1 = new Div(cat, titleSpan);
        row1.getStyle().set("display", "flex");
        row1.getStyle().set("gap", "8px");
        row1.getStyle().set("align-items", "center");
        row1.getStyle().set("flex-wrap", "wrap");

        Span summary = new Span(a.getSummary() == null ? "" : a.getSummary());
        summary.getStyle().set("color", "var(--lumo-secondary-text-color)");
        summary.getStyle().set("font-size", "13px");
        summary.getStyle().set("display", "block");
        summary.getStyle().set("margin", "4px 0");

        Anchor link = new Anchor(a.getUrl(), "元記事を開く ↗");
        link.setTarget("_blank");
        link.getElement().setAttribute("rel", "noopener noreferrer");
        Span source = new Span("🌐 情報源#" + a.getSourceId());
        source.getStyle().set("color", "var(--lumo-secondary-text-color)");
        source.getStyle().set("font-size", "12px");
        Div meta = new Div(source, link);
        meta.getStyle().set("display", "flex");
        meta.getStyle().set("gap", "12px");
        meta.getStyle().set("align-items", "center");

        Div body = new Div(row1, summary, meta);
        body.getStyle().set("flex", "1");

        Div card = new Div(dateBox, noimg, body);
        card.addClassName("art-card");
        card.getStyle().set("display", "flex");
        card.getStyle().set("gap", "14px");
        card.getStyle().set("width", "100%");
        return card;
    }

    private String categoryLabel(Category c) {
        return switch (c) {
            case GOODS -> "グッズ";
            case ANIME -> "アニメ";
            case MANGA -> "漫画";
            case EVENT -> "イベント";
            case GAME -> "ゲーム";
            case ARCADE -> "ゲームセンター";
            case CAPSULE_TOY -> "カプセルトイ";
            case OTHER -> "その他";
        };
    }
}
