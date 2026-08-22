package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.stream.Collectors;

/**
 * テーマ管理（SC-04）。テーマ登録・編集（キーワード/対象カテゴリ/収集の有効無効/削除）を各自の分だけ行う。
 * 一覧は「キーワード」「対象カテゴリ」＋行ごとの「編集」ボタンに絞り、編集操作は編集ダイアログに集約する。
 * 業務ロジックはリポジトリに委ねる（DD-CLS-11）。
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

        // --- 一覧（キーワード / 対象カテゴリ / 行ごとの編集ボタン） ---
        // 収集停止中のテーマは一覧から消えると再有効化できないため、停止中はキーワード欄に注記して残す。
        grid.addColumn(t -> t.isActive() ? t.getKeyword() : t.getKeyword() + "（収集停止中）")
                .setHeader("キーワード").setAutoWidth(true);
        grid.addColumn(t -> t.getCategories().stream().map(this::categoryLabel).collect(Collectors.joining("・")))
                .setHeader("対象カテゴリ").setAutoWidth(true);
        grid.addComponentColumn(t -> {
            Button edit = new Button("編集", e -> openEditDialog(t.getId()));
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return edit;
        }).setHeader("");
        grid.setWidthFull();

        add(title, form, grid);
        refresh();
    }

    /**
     * 編集ダイアログ。キーワード・対象カテゴリ・収集の有効無効を編集し、保存/削除できる。
     * グリッド保持のインスタンスではなくDBの最新を取り直す（表示が古くても実状態を出す）。
     */
    private void openEditDialog(Long themeId) {
        ThemeEntity theme = themes.findById(themeId).orElse(null);
        if (theme == null) { Notification.show("テーマが見つかりません。"); refresh(); return; }
        final String originalKeyword = theme.getKeyword();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("テーマの編集");

        TextField keyword = new TextField("キーワード");
        keyword.setWidthFull();
        keyword.setValue(theme.getKeyword());

        CheckboxGroup<Category> categories = new CheckboxGroup<>("収集対象カテゴリ");
        categories.setItems(Category.values());
        categories.setItemLabelGenerator(this::categoryLabel);
        categories.setValue(theme.getCategories());

        Checkbox active = new Checkbox("収集を有効にする", theme.isActive());

        Button save = new Button("保存", e -> {
            String kw = keyword.getValue() == null ? "" : keyword.getValue().trim();
            if (kw.isEmpty()) { Notification.show("キーワードを入力してください。"); return; }
            if (categories.getSelectedItems().isEmpty()) { Notification.show("カテゴリを1つ以上選択してください。"); return; }
            // キーワードを変えた場合のみ、他テーマとの重複を確認（自分自身は除外）。
            if (!kw.equals(originalKeyword) && themes.existsByUserIdAndKeyword(USER_ID, kw)) {
                Notification.show("同じキーワードのテーマがすでにあります。"); return;
            }
            try {
                theme.setKeyword(kw);
                theme.setCategories(categories.getSelectedItems());
                theme.setActive(active.getValue());
                themes.save(theme);
                dialog.close();
                refresh();
                Notification.show("保存しました。（収集=" + (active.getValue() ? "有効" : "停止") + "）");
            } catch (Exception ex) {
                Notification.show("保存に失敗しました: " + ex.getClass().getSimpleName() + " - " + ex.getMessage(),
                        8000, Notification.Position.MIDDLE);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button delete = new Button("削除", e -> {
            try {
                themes.deleteById(themeId);   // article_theme_matches は ON DELETE CASCADE で連動削除
                dialog.close();
                refresh();
                Notification.show("削除しました。");
            } catch (Exception ex) {
                Notification.show("削除に失敗しました: " + ex.getMessage(), 6000, Notification.Position.MIDDLE);
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        Button cancel = new Button("キャンセル", e -> dialog.close());

        dialog.add(new VerticalLayout(keyword, categories, active));
        // 左に削除、右にキャンセル/保存を置いて誤操作を避ける。
        HorizontalLayout footer = new HorizontalLayout(delete, cancel, save);
        footer.setWidthFull();
        footer.setFlexGrow(1, delete);   // 削除を左端へ押しやる
        dialog.getFooter().add(footer);
        dialog.open();
    }

    private void refresh() {
        // 自分のテーマだけを表示（有効/停止を問わず全件。停止中も編集で再有効化できるよう残す）。
        grid.setItems(themes.findByUserIdOrderByKeyword(USER_ID));
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
