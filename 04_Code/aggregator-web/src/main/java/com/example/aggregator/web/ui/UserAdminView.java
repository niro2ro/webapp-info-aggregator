package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.domain.model.UserRole;
import com.example.aggregator.infra.service.UserService;
import com.example.aggregator.web.security.AdminOnly;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * 利用者登録・管理（SC-09・admin のみ）。{@link AdminOnly} なので非 admin はガードが到達を拒否する。
 * ここでのみ「編集・削除・Admin付与・管理者PIN設定」を行う（自己登録 SC-10 は role=User 固定）。
 */
@Route(value = "users", layout = MainLayout.class)
@PageTitle("利用者管理 | 情報収集ツール")
public class UserAdminView extends VerticalLayout implements AdminOnly {

    private final UserService users;
    private final Grid<UserEntity> grid = new Grid<>(UserEntity.class, false);

    public UserAdminView(UserService users) {
        this.users = users;
        setSizeFull();
        setPadding(true);

        H2 title = new H2("利用者管理");
        title.addClassName("view-title");

        Button add = new Button("＋ 利用者を追加", e -> openForm(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        grid.addColumn(UserEntity::getDisplayName).setHeader("表示名").setAutoWidth(true);
        grid.addColumn(u -> u.getRole() == UserRole.ADMIN ? "管理者" : "一般").setHeader("ロール").setAutoWidth(true);
        grid.addColumn(u -> u.getRole() == UserRole.ADMIN ? (u.hasAdminPin() ? "設定済" : "未設定") : "—")
                .setHeader("管理者PIN").setAutoWidth(true);
        grid.addColumn(u -> u.isActive() ? "有効" : "無効").setHeader("状態").setAutoWidth(true);
        grid.addComponentColumn(u -> {
            Button edit = new Button("編集", e -> openForm(u));
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button del = new Button("削除", e -> {
                try {
                    users.adminDelete(u.getId());
                    refresh();
                    Notification.show("削除しました。");
                } catch (RuntimeException ex) {
                    Notification.show("削除できませんでした（関連データがある可能性）。無効化をご検討ください。");
                }
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(edit, del);
        }).setHeader("操作");
        grid.setWidthFull();

        add(title, add, grid);
        refresh();
    }

    /** 追加/編集フォーム（editUser=null なら新規）。 */
    private void openForm(UserEntity editUser) {
        boolean isEdit = editUser != null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isEdit ? "利用者の編集" : "利用者の追加");

        TextField name = new TextField("表示名");
        name.setWidthFull();
        Select<UserRole> role = new Select<>();
        role.setLabel("ロール");
        role.setItems(UserRole.values());
        role.setItemLabelGenerator(r -> r == UserRole.ADMIN ? "管理者（Admin付与）" : "一般（User）");
        role.setValue(isEdit ? editUser.getRole() : UserRole.USER);
        TextField pin = new TextField(isEdit ? "管理者PIN（変更する場合のみ・数字4桁）" : "管理者PIN（数字4桁）");
        pin.setMaxLength(4);
        pin.setPattern("\\d{4}");
        Checkbox active = new Checkbox("有効", isEdit ? editUser.isActive() : true);
        if (isEdit) name.setValue(editUser.getDisplayName());

        // ロールが管理者のときだけ PIN 欄を表示する。
        Runnable syncPin = () -> pin.setVisible(role.getValue() == UserRole.ADMIN);
        role.addValueChangeListener(e -> syncPin.run());
        syncPin.run();

        Button save = new Button("保存", e -> {
            try {
                if (isEdit) {
                    users.adminUpdate(editUser.getId(), name.getValue(), role.getValue(), active.getValue(),
                            pin.isVisible() ? emptyToNull(pin.getValue()) : null);
                } else {
                    users.adminCreate(name.getValue(), role.getValue(),
                            pin.isVisible() ? pin.getValue() : null, active.getValue());
                }
                dialog.close();
                refresh();
                Notification.show("保存しました。");
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("キャンセル", e -> dialog.close());

        dialog.add(new VerticalLayout(name, role, pin, active));
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private void refresh() {
        grid.setItems(users.all());
    }
}
