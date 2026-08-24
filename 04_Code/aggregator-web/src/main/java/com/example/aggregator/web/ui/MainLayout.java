package com.example.aggregator.web.ui;

import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;

/**
 * 共通レイアウト（AppLayout: 上部バー＋左ドロワー・BD-SC-00-01）。上部バーは「アプリ名・利用者名・ログアウト」のみ
 * （ロール切替UIは置かない・画面設計 §66）。管理系メニューは admin のときだけ表示する（BD-SC-00-06）。
 * 到達拒否は {@link com.example.aggregator.web.security.AuthGuard} が担保（メニュー非表示は UX 上の補助）。
 */
public class MainLayout extends AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);

        // --- 上部バー ---
        Span title = new Span("📚 テーマ別最新情報アグリゲーター");
        title.addClassName("app-title");

        String userName = CurrentUser.get().map(CurrentUser.Info::displayName).orElse("ゲスト");
        Span user = new Span((CurrentUser.isAdmin() ? "🔒 " : "👤 ") + userName);
        user.getStyle().set("color", "#f2ecdd").set("font-size", "13px");
        Button logout = new Button("ログアウト", e -> {
            CurrentUser.logout();
            UI.getCurrent().navigate(LoginView.class);
        });
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        logout.getStyle().set("color", "#f2ecdd");

        HorizontalLayout navbar = new HorizontalLayout(new DrawerToggle(), title);
        navbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.setWidthFull();
        navbar.setSpacing(true);
        navbar.setPadding(false);
        HorizontalLayout right = new HorizontalLayout(user, logout);
        right.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.addAndExpand(new Span()); // スペーサー
        navbar.add(right);
        navbar.getStyle().set("padding", "0 16px");
        addToNavbar(navbar);

        // --- 左ドロワー（共通・画面設計 §94 / モックアップ準拠） ---
        VerticalLayout drawer = new VerticalLayout(
                new RouterLink("🗒  タイムライン", TimelineView.class),
                new RouterLink("🏷  テーマ管理", ThemeView.class),
                new RouterLink("🔔  LINE通知設定", NotificationSettingsView.class));
        drawer.setSpacing(true);
        drawer.setPadding(true);

        // --- 管理グループ（admin のみ・SC-06〜09。メニュー非表示＋到達拒否の二重防御・BD-SC-00-06） ---
        if (CurrentUser.isAdmin()) {
            Span adminLabel = new Span("管理（admin のみ）");
            adminLabel.getStyle().set("font-size", "11px").set("color", "var(--lumo-secondary-text-color)")
                    .set("margin-top", "8px");
            drawer.add(new Hr(), adminLabel,
                    new RouterLink("🌐  情報源マスタ", SourceMasterView.class),
                    new RouterLink("📊  管理ダッシュボード", AdminDashboardView.class),
                    new RouterLink("📜  実行ログ", ExecutionLogView.class),
                    new RouterLink("👥  利用者管理", UserAdminView.class));
        }
        addToDrawer(drawer);
    }
}
