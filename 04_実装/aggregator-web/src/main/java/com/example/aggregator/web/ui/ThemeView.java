package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * テーマ管理（SC-04）。テーマ登録/削除・対象カテゴリ指定（各自の分）。Phase 1 は認証前のため利用者は固定
 * （id=2）。Phase 5 でログイン利用者に紐づける。業務ロジックはリポジトリ/サービスに委ねる（DD-CLS-11）。
 */
@Route(value = "themes", layout = MainLayout.class)
@PageTitle("テーマ管理 | アグリゲーター")
public class ThemeView extends VerticalLayout {

    private final Long USER_ID = CurrentUser.get().map(CurrentUser.Info::id).orElse(-1L);

    private final ThemeRepository themes;
    private final Grid<ThemeEntity> grid = new Grid<>(ThemeEntity.class, false);

    public ThemeView(ThemeRepository themes) {
        this.themes = themes;
        setSizeFull();
        setPadding(true);

        H2 title = new H2("テーマ管理");
        title.addClassName("view-title");

        // --- 追加フォーム ---
        TextField keyword = new TextField("キーワード");
        keyword.setPlaceholder("例：呪術廻戦");
        CheckboxGroup<Category> categories = new CheckboxGroup<>("収集対象カテゴリ");
        categories.setItems(Category.values());
        categories.setItemLabelGenerator(this::categoryLabel);
        Button add = new Button("追加する", e -> {
            String kw = keyword.getValue() == null ? "" : keyword.getValue().trim();
            if (kw.isEmpty()) { Notification.show("キーワードを入力してください。"); return; }
            if (categories.getSelectedItems().isEmpty()) { Notification.show("カテゴリを1つ以上選択してください。"); return; }
            if (themes.existsByUserIdAndKeyword(USER_ID, kw)) { Notification.show("同じキーワードのテーマがすでにあります。"); return; }
            themes.save(new ThemeEntity(USER_ID, kw, categories.getSelectedItems()));
            keyword.clear();
            categories.clear();
            refresh();
            Notification.show("追加しました。");
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout form = new HorizontalLayout(keyword, categories, add);
        form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        form.setWidthFull();

        // --- 一覧 ---
        grid.addColumn(ThemeEntity::getKeyword).setHeader("キーワード").setAutoWidth(true);
        grid.addColumn(t -> t.getCategories().stream().map(this::categoryLabel).collect(Collectors.joining("・")))
                .setHeader("対象カテゴリ").setAutoWidth(true);
        grid.addColumn(t -> t.isActive() ? "有効" : "停止中").setHeader("収集").setAutoWidth(true);
        grid.addComponentColumn(t -> {
            Button del = new Button("削除", e -> {
                themes.deleteById(t.getId());
                refresh();
                Notification.show("削除しました。");
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return del;
        }).setHeader("操作");
        grid.setWidthFull();

        add(title, form, grid);
        refresh();
    }

    private void refresh() {
        grid.setItems(themes.findByActiveTrue());
    }

    private String categoryLabel(Category c) {
        return categoryLabelStatic(c);
    }

    /** カテゴリの日本語ラベル（他ビューと共有）。 */
    static String categoryLabelStatic(Category c) {
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
