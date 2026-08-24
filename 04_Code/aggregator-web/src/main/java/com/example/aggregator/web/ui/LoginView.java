package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.domain.model.UserRole;
import com.example.aggregator.infra.service.UserService;
import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * ログイン（SC-01）。有効な利用者を一覧表示し、一般利用者はクリックだけでログイン、管理者は4桁PINを求める。
 * 未認証で到達できる公開画面のため <b>MainLayout（ドロワー）を付けない</b>（BD-SC-01・画面設計 §96）。
 */
@Route("login")
@PageTitle("ログイン | 情報収集ツール")
public class LoginView extends VerticalLayout {

    private final UserService users;

    public LoginView(UserService users) {
        this.users = users;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName("login-view");

        VerticalLayout card = new VerticalLayout();
        card.addClassName("login-card");
        card.setWidth("360px");
        card.setPadding(true);
        card.setSpacing(true);
        card.setAlignItems(Alignment.STRETCH);

        H1 title = new H1("📚 情報収集ツール");
        title.getStyle().set("font-size", "22px").set("margin", "0 0 4px 0");
        Span sub = new Span("利用者を選んでください");
        sub.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "13px");
        card.add(title, sub);

        for (UserEntity u : users.activeUsers()) {
            card.add(userButton(u));
        }

        Button signup = new Button("＋ 新規登録", e -> UI.getCurrent().navigate(SignupView.class));
        signup.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        card.add(signup);

        add(card);
    }

    /** 利用者1人ぶんの選択ボタン。管理者は鍵マークで区別（BD-SC-01-01）。 */
    private Button userButton(UserEntity u) {
        boolean admin = u.getRole() == UserRole.ADMIN;
        Button b = new Button((admin ? "🔒 " : "👤 ") + u.getDisplayName());
        b.setWidthFull();
        b.addThemeVariants(admin ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
        b.addClickListener(e -> {
            if (admin) {
                onAdminSelected(u);
            } else {
                loginAs(u);   // 一般利用者: クレデンシャル無しでログイン（BD-SC-01-02）
            }
        });
        return b;
    }

    private void onAdminSelected(UserEntity admin) {
        // PIN 未設定（seed 直後など）はブートストラップとして PIN 無しで通す。
        if (!admin.hasAdminPin()) {
            loginAs(admin);
            Notification.show("管理者PINが未設定です。ログイン後「利用者管理」で設定してください。", 5000, Notification.Position.MIDDLE);
            return;
        }
        openPinDialog(admin);
    }

    /** 4桁PIN入力ダイアログ（BD-SC-01-03/04/05）。 */
    private void openPinDialog(UserEntity admin) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(admin.getDisplayName() + " の管理者PIN");

        TextField pin = new TextField("4桁PIN");
        pin.setMaxLength(4);
        pin.setPattern("\\d{4}");
        pin.setPlaceholder("••••");
        pin.setWidthFull();
        Span error = new Span();
        error.getStyle().set("color", "var(--lumo-error-text-color)").set("font-size", "13px");

        Button ok = new Button("ログイン", e -> {
            UserService.PinCheck check = users.verifyAdminPin(admin.getId(), pin.getValue());
            switch (check.status()) {
                case OK -> { dialog.close(); loginAs(admin); }
                case WRONG -> { error.setText("PINが違います。"); pin.clear(); pin.focus(); }
                case LOCKED -> error.setText("試行回数の上限です。約 "
                        + Math.max(1, check.lockRemainingSeconds() / 60) + " 分後に再試行してください。");
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ok.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
        Button cancel = new Button("キャンセル", e -> dialog.close());

        dialog.add(new VerticalLayout(pin, error));
        dialog.getFooter().add(cancel, ok);
        dialog.open();
        pin.focus();
    }

    private void loginAs(UserEntity u) {
        CurrentUser.set(new CurrentUser.Info(u.getId(), u.getDisplayName(), u.getRole()));
        UI.getCurrent().navigate(TimelineView.class);
    }
}
