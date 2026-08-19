package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.service.SourceService;
import com.example.aggregator.web.security.AdminOnly;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * 情報源マスタ管理（SC-06・admin 限定）。<b>規約ゲート</b>を運用する画面: {@code terms_reviewed=true} かつ
 * {@code is_active=true} の情報源だけが収集対象になる。削除は不可（ON DELETE RESTRICT）、停止は無効化で行う。
 */
@Route(value = "sources", layout = MainLayout.class)
@PageTitle("情報源マスタ | アグリゲーター")
public class SourceMasterView extends VerticalLayout implements AdminOnly {

    private final SourceService sources;
    private final Grid<SourceEntity> grid = new Grid<>(SourceEntity.class, false);

    public SourceMasterView(SourceService sources) {
        this.sources = sources;
        setSizeFull();
        setPadding(true);

        H2 title = new H2("情報源マスタ");
        title.addClassName("view-title");
        Span gate = new Span("収集対象になるのは「有効 かつ 規約確認済」の情報源のみ。未確認は収集されません（規約ゲート）。");
        gate.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "13px");

        Button add = new Button("＋ 情報源を追加", e -> openForm(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        grid.addColumn(SourceEntity::getName).setHeader("名称").setAutoWidth(true);
        grid.addColumn(SourceEntity::getUrl).setHeader("URL").setAutoWidth(true);
        grid.addColumn(s -> fetchLabel(s.getFetchType())).setHeader("取得方式").setAutoWidth(true);
        grid.addColumn(s -> s.isActive() ? "有効" : "無効").setHeader("有効").setAutoWidth(true);
        grid.addColumn(s -> s.isTermsReviewed() ? "確認済" : "未確認").setHeader("規約").setAutoWidth(true);
        grid.addColumn(s -> s.getTermsReviewedAt() != null ? s.getTermsReviewedAt().toString() : "—")
                .setHeader("規約確認日").setAutoWidth(true);
        grid.addColumn(s -> s.isRobotsRespect() ? "尊重" : "無視").setHeader("robots").setAutoWidth(true);
        grid.addColumn(s -> s.getLastFetchedAt() != null ? s.getLastFetchedAt().toString() : "—")
                .setHeader("最終取得").setAutoWidth(true);
        grid.addComponentColumn(s -> {
            Button edit = new Button("編集", e -> openForm(s));
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return edit;
        }).setHeader("操作");
        grid.setWidthFull();

        add(title, gate, add, grid);
        refresh();
    }

    private void openForm(SourceEntity row) {
        boolean isEdit = row != null;
        // 編集時はグリッド保持のインスタンスではなくDBの最新を取り直す（表示が古くても実状態を出す）。
        SourceEntity edit = isEdit ? sources.find(row.getId()).orElse(row) : null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isEdit ? "情報源の編集" : "情報源の追加");

        TextField name = new TextField("名称");
        name.setWidthFull();
        TextField url = new TextField("URL");
        url.setWidthFull();
        Select<FetchType> fetch = new Select<>();
        fetch.setLabel("取得方式");
        fetch.setItems(FetchType.values());
        fetch.setItemLabelGenerator(SourceMasterView::fetchLabel);
        fetch.setValue(isEdit ? edit.getFetchType() : FetchType.RSS);
        Checkbox active = new Checkbox("有効", isEdit ? edit.isActive() : true);
        Checkbox reviewed = new Checkbox("規約確認済（＝収集を許可）", isEdit && edit.isTermsReviewed());
        Checkbox robots = new Checkbox("robots.txt を尊重", isEdit ? edit.isRobotsRespect() : true);
        TextArea note = new TextArea("規約メモ");
        note.setWidthFull();
        if (isEdit) {
            name.setValue(edit.getName());
            url.setValue(edit.getUrl());
            if (edit.getTermsNote() != null) note.setValue(edit.getTermsNote());
        }

        Button save = new Button("保存", e -> {
            try {
                if (isEdit) {
                    sources.update(edit.getId(), name.getValue(), url.getValue(), fetch.getValue(),
                            active.getValue(), reviewed.getValue(), note.getValue(), robots.getValue());
                } else {
                    sources.create(name.getValue(), url.getValue(), fetch.getValue(),
                            active.getValue(), reviewed.getValue(), note.getValue(), robots.getValue());
                }
                dialog.close();
                refresh();
                Notification.show("保存しました。（有効=" + active.getValue() + " / 規約確認済="
                        + reviewed.getValue() + " / robots尊重=" + robots.getValue() + "）");
            } catch (IllegalArgumentException ex) {
                // 入力チェック（名称/URL未入力など）。ユーザーが直せる想定のメッセージ。
                Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE);
            } catch (Exception ex) {
                // それ以外の失敗（DB制約・接続など）を握りつぶさず理由を表示する（不具合切り分け）。
                Notification.show("保存に失敗しました: " + ex.getClass().getSimpleName()
                        + " - " + ex.getMessage(), 8000, Notification.Position.MIDDLE);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("キャンセル", e -> dialog.close());

        dialog.add(new VerticalLayout(name, url, fetch, active, reviewed, robots, note));
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void refresh() {
        grid.setItems(sources.all());
    }

    static String fetchLabel(FetchType t) {
        return switch (t) {
            case RSS -> "RSS";
        };
    }
}
