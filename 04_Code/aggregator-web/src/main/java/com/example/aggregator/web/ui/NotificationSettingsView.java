package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.service.ArticleInteractionService;
import com.example.aggregator.infra.service.FavoriteService;
import com.example.aggregator.infra.service.UserService;
import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.Map;

/**
 * LINE通知設定（SC-05・旧「お気に入り管理」を改称/拡張・Q26）。LINE通知に関わる本人設定を1画面に集約する。
 *
 * <ul>
 *   <li><b>上段＝LINEに繋ぐ設定</b>: line_user_id 登録／通知の全体ON/OFF／連携ステータス＋友だち追加案内</li>
 *   <li><b>下段＝何を通知するか</b>: テーマお気に入り／情報源お気に入り（各「お気に入り」＋「通知ON/OFF」）／ブックマーク</li>
 * </ul>
 * 通知が届く条件: ①お気に入り登録 ②その通知ON ③全体通知ON ④LINE ID登録済 ⑤通数枠あり（画面にも明示）。
 * 「お気に入り＝通知する／ブックマーク＝後で見るだけ（通知しない）」。
 */
@Route(value = "notify", layout = MainLayout.class)
@PageTitle("LINE通知設定 | アグリゲーター")
public class NotificationSettingsView extends VerticalLayout {

    private final Long userId = CurrentUser.get().map(CurrentUser.Info::id).orElse(-1L);

    private final ThemeRepository themes;
    private final SourceRepository sources;
    private final ArticleRepository articles;
    private final FavoriteService favorites;
    private final ArticleInteractionService interaction;
    private final UserService users;

    private final Grid<ThemeEntity> themeGrid = new Grid<>(ThemeEntity.class, false);
    private final Grid<SourceEntity> sourceGrid = new Grid<>(SourceEntity.class, false);
    private final Grid<ArticleEntity> bookmarkGrid = new Grid<>(ArticleEntity.class, false);

    public NotificationSettingsView(ThemeRepository themes, SourceRepository sources, ArticleRepository articles,
                                    FavoriteService favorites, ArticleInteractionService interaction,
                                    UserService users) {
        this.themes = themes;
        this.sources = sources;
        this.articles = articles;
        this.favorites = favorites;
        this.interaction = interaction;
        this.users = users;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("LINE通知設定");
        title.addClassName("view-title");

        add(title, buildLineBlock(), buildTabs());
        refresh();
    }

    // ===== 上段: 自分のLINE連携（BD-SC-05-05〜08） =====
    private VerticalLayout buildLineBlock() {
        VerticalLayout block = new VerticalLayout();
        block.addClassName("line-link-card");
        block.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px").set("background", "var(--lumo-base-color)");
        block.setPadding(true);
        block.setSpacing(true);
        block.setWidthFull();

        UserEntity me = users.find(userId).orElse(null);

        H3 h = new H3("自分のLINE連携");
        h.getStyle().set("margin", "0");

        TextField lineId = new TextField("LINEユーザーID");
        lineId.setPlaceholder("Uxxxxxxxx（LINEの宛先ID）");
        lineId.setWidth("320px");
        if (me != null && me.getLineUserId() != null) lineId.setValue(me.getLineUserId());

        Checkbox notifyAll = new Checkbox("通知を受け取る（全体ON/OFF）");
        notifyAll.setValue(me == null || me.isNotifyEnabled());

        Span status = new Span();
        applyStatus(status, me);

        Button save = new Button("保存", e -> {
            try {
                users.updateLineSettings(userId, lineId.getValue(), notifyAll.getValue());
                applyStatus(status, users.find(userId).orElse(null));
                Notification.show("LINE連携設定を保存しました。");
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout row = new HorizontalLayout(lineId, notifyAll, save);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        row.setSpacing(true);

        // 未追加・ブロック時の自己解決導線（③を受信側が解決・BD-SC-05-08）。
        Anchor addFriend = new Anchor("https://line.me/", "▶ LINEで友だち追加する（未追加・ブロック時）");
        addFriend.setTarget("_blank");
        addFriend.getStyle().set("font-size", "13px");

        Span cond = new Span("通知が届く条件: ①お気に入り登録 ②その通知ON ③全体通知ON ④LINE ID登録済 ⑤通数枠あり");
        cond.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");

        block.add(h, row, status, addFriend, cond);
        return block;
    }

    /** 連携ステータス表示（簡易: ID・全体ONの有無から判定）。実送信の失敗表示は実行ログ/通知実績に基づく拡張余地。 */
    private void applyStatus(Span status, UserEntity me) {
        boolean hasId = me != null && me.getLineUserId() != null && !me.getLineUserId().isBlank();
        boolean on = me != null && me.isNotifyEnabled();
        String text;
        String color;
        if (!hasId) { text = "未連携（LINEユーザーID未登録 → 通知は届きません）"; color = "var(--lumo-error-text-color)"; }
        else if (!on) { text = "連携OK・ただし全体通知OFF（届きません）"; color = "var(--lumo-secondary-text-color)"; }
        else { text = "連携OK（通知が届きます）"; color = "#4e7d55"; }
        status.setText("連携ステータス: " + text);
        status.getStyle().set("color", color).set("font-size", "13px").set("font-weight", "600");
    }

    // ===== 下段: お気に入り／ブックマーク（タブ・BD-SC-05-01） =====
    private TabSheet buildTabs() {
        buildThemeGrid();
        buildSourceGrid();
        buildBookmarkGrid();
        Span note = new Span("お気に入り＝通知する／ブックマーク＝後で見るだけ（通知しません）");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("テーマお気に入り", themeGrid);
        tabs.add("情報源お気に入り", sourceGrid);
        tabs.add("ブックマーク", new VerticalLayout(note, bookmarkGrid));
        return tabs;
    }

    private void buildThemeGrid() {
        themeGrid.addColumn(ThemeEntity::getKeyword).setHeader("テーマ").setAutoWidth(true);
        themeGrid.addComponentColumn(t -> {
            Map<Long, Boolean> favs = favorites.themeFavorites(userId);
            boolean fav = favs.containsKey(t.getId());
            Button b = new Button(fav ? "★ お気に入り中" : "☆ お気に入り", e -> {
                favorites.toggleThemeFavorite(userId, t.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("お気に入り");
        themeGrid.addComponentColumn(t -> {
            Map<Long, Boolean> favs = favorites.themeFavorites(userId);
            if (!favs.containsKey(t.getId())) return new Span("—");
            Checkbox c = new Checkbox("通知", favs.get(t.getId()));
            c.addValueChangeListener(e -> favorites.setThemeNotify(userId, t.getId(), e.getValue()));
            return c;
        }).setHeader("通知ON/OFF");
        themeGrid.setWidthFull();
    }

    private void buildSourceGrid() {
        sourceGrid.addColumn(SourceEntity::getName).setHeader("情報源").setAutoWidth(true);
        sourceGrid.addComponentColumn(s -> {
            Map<Long, Boolean> favs = favorites.sourceFavorites(userId);
            boolean fav = favs.containsKey(s.getId());
            Button b = new Button(fav ? "★ お気に入り中" : "☆ お気に入り", e -> {
                favorites.toggleSourceFavorite(userId, s.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("お気に入り");
        sourceGrid.addComponentColumn(s -> {
            Map<Long, Boolean> favs = favorites.sourceFavorites(userId);
            if (!favs.containsKey(s.getId())) return new Span("—");
            Checkbox c = new Checkbox("通知", favs.get(s.getId()));
            c.addValueChangeListener(e -> favorites.setSourceNotify(userId, s.getId(), e.getValue()));
            return c;
        }).setHeader("通知ON/OFF");
        sourceGrid.setWidthFull();
    }

    private void buildBookmarkGrid() {
        bookmarkGrid.addColumn(ArticleEntity::getTitle).setHeader("記事（後で見る）").setAutoWidth(true);
        bookmarkGrid.addColumn(a -> a.getEventDate() != null ? a.getEventDate().toString() : "(日付不明)")
                .setHeader("発生日").setAutoWidth(true);
        bookmarkGrid.addComponentColumn(a -> {
            Button b = new Button("解除", e -> {
                interaction.toggleBookmark(userId, a.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("操作");
        bookmarkGrid.setWidthFull();
    }

    private void refresh() {
        // テーマは自分の分だけ（アカウントごと）。情報源は共有だが内部の検索用ソース(active=false)は隠す。
        themeGrid.setItems(themes.findByUserIdAndActiveTrueOrderByKeyword(userId));
        sourceGrid.setItems(sources.findAll().stream().filter(SourceEntity::isActive).toList());
        bookmarkGrid.setItems(articles.findBookmarkedByUser(userId));
    }
}
