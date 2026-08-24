package com.example.aggregator.web.ui;

import com.example.aggregator.infra.service.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * 新規アカウント登録（SC-10・未認証で誰でも・PIN不要）。表示名だけを受け取り role=User で登録する。
 * Admin 付与・PIN 設定はここではできない（権限を伴う操作は管理者の SC-09 に集約・Q27）。
 * 公開画面のため MainLayout は付けない。
 */
@Route("signup")
@PageTitle("新規登録 | 情報収集ツール")
public class SignupView extends VerticalLayout {

    public SignupView(UserService users) {
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

        H1 title = new H1("新規登録");
        title.getStyle().set("font-size", "20px").set("margin", "0");
        Span note = new Span("表示名だけで登録できます（一般利用者）。");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "13px");

        TextField name = new TextField("表示名");
        name.setWidthFull();
        name.setPlaceholder("例：アンパンマン");

        Button register = new Button("登録する", e -> {
            try {
                users.selfRegister(name.getValue());
                Notification.show("登録しました。ログインしてください。");
                UI.getCurrent().navigate(LoginView.class);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });
        register.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        register.setWidthFull();
        register.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button back = new Button("‹ ログイン画面へ戻る", e -> UI.getCurrent().navigate(LoginView.class));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        card.add(title, note, name, register, back);
        add(card);
    }
}
