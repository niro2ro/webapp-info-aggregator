package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.service.ArticleInteractionService;
import com.example.aggregator.infra.service.FavoriteService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.Map;

/**
 * お気に入り／通知設定（SC-05 の Phase 3 版）。お気に入り＝通知する／ブックマーク＝後で見るだけ（通知しない）
 * を分けて扱う（用語定義 §1）。LINE連携の設定は Phase 4 で追加する。利用者は暫定固定（id=2）。
 */
@Route(value = "favorites", layout = MainLayout.class)
@PageTitle("お気に入り | アグリゲーター")
public class FavoritesView extends VerticalLayout {

    private static final Long USER_ID = 2L;

    private final ThemeRepository themes;
    private final SourceRepository sources;
    private final ArticleRepository articles;
    private final FavoriteService favorites;
    private final ArticleInteractionService interaction;

    private final Grid<ThemeEntity> themeGrid = new Grid<>(ThemeEntity.class, false);
    private final Grid<SourceEntity> sourceGrid = new Grid<>(SourceEntity.class, false);
    private final Grid<ArticleEntity> bookmarkGrid = new Grid<>(ArticleEntity.class, false);

    public FavoritesView(ThemeRepository themes, SourceRepository sources, ArticleRepository articles,
                         FavoriteService favorites, ArticleInteractionService interaction) {
        this.themes = themes;
        this.sources = sources;
        this.articles = articles;
        this.favorites = favorites;
        this.interaction = interaction;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("お気に入り");
        title.addClassName("view-title");
        Span note = new Span("お気に入り＝通知する／ブックマーク＝後で見るだけ（通知しません）");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");

        buildThemeGrid();
        buildSourceGrid();
        buildBookmarkGrid();

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("テーマお気に入り", themeGrid);
        tabs.add("情報源お気に入り", sourceGrid);
        tabs.add("ブックマーク", bookmarkGrid);

        add(title, note, tabs);
        refresh();
    }

    private void buildThemeGrid() {
        themeGrid.addColumn(ThemeEntity::getKeyword).setHeader("テーマ").setAutoWidth(true);
        themeGrid.addComponentColumn(t -> {
            Map<Long, Boolean> favs = favorites.themeFavorites(USER_ID);
            boolean fav = favs.containsKey(t.getId());
            Button b = new Button(fav ? "★ お気に入り中" : "☆ お気に入り", e -> {
                favorites.toggleThemeFavorite(USER_ID, t.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("お気に入り");
        themeGrid.addComponentColumn(t -> {
            Map<Long, Boolean> favs = favorites.themeFavorites(USER_ID);
            if (!favs.containsKey(t.getId())) return new Span("—");
            Checkbox c = new Checkbox("通知", favs.get(t.getId()));
            c.addValueChangeListener(e -> favorites.setThemeNotify(USER_ID, t.getId(), e.getValue()));
            return c;
        }).setHeader("通知");
        themeGrid.setWidthFull();
    }

    private void buildSourceGrid() {
        sourceGrid.addColumn(SourceEntity::getName).setHeader("情報源").setAutoWidth(true);
        sourceGrid.addComponentColumn(s -> {
            Map<Long, Boolean> favs = favorites.sourceFavorites(USER_ID);
            boolean fav = favs.containsKey(s.getId());
            Button b = new Button(fav ? "★ お気に入り中" : "☆ お気に入り", e -> {
                favorites.toggleSourceFavorite(USER_ID, s.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("お気に入り");
        sourceGrid.addComponentColumn(s -> {
            Map<Long, Boolean> favs = favorites.sourceFavorites(USER_ID);
            if (!favs.containsKey(s.getId())) return new Span("—");
            Checkbox c = new Checkbox("通知", favs.get(s.getId()));
            c.addValueChangeListener(e -> favorites.setSourceNotify(USER_ID, s.getId(), e.getValue()));
            return c;
        }).setHeader("通知");
        sourceGrid.setWidthFull();
    }

    private void buildBookmarkGrid() {
        bookmarkGrid.addColumn(ArticleEntity::getTitle).setHeader("記事（後で見る）").setAutoWidth(true);
        bookmarkGrid.addColumn(a -> a.getEventDate() != null ? a.getEventDate().toString() : "(日付不明)")
                .setHeader("発生日").setAutoWidth(true);
        bookmarkGrid.addComponentColumn(a -> {
            Button b = new Button("解除", e -> {
                interaction.toggleBookmark(USER_ID, a.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("操作");
        bookmarkGrid.setWidthFull();
    }

    private void refresh() {
        themeGrid.setItems(themes.findByActiveTrue());
        sourceGrid.setItems(sources.findAll());
        bookmarkGrid.setItems(articles.findBookmarkedByUser(USER_ID));
    }
}
