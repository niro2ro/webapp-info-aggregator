package com.example.aggregator.web.ui;

import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
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
        // 絵文字は使わず、和文の見出し（明朝）だけで名乗る。装飾を足さないことで
        // “既製テンプレ感”を避ける（和モダン・ミニマル）。
        Span title = new Span("テーマ別最新情報アグリゲーター");
        title.addClassName("app-title");

        String userName = CurrentUser.get().map(CurrentUser.Info::displayName).orElse("ゲスト");
        // ロール表示も絵文字ではなく統一アイコン（管理者=盾／一般=人物）で示す。
        Icon roleIcon = (CurrentUser.isAdmin() ? VaadinIcon.SHIELD : VaadinIcon.USER).create();
        roleIcon.getStyle().set("width", "15px").set("height", "15px").set("color", "#cdbfa0");
        Span user = new Span(userName);
        user.getStyle().set("color", "#f2ecdd").set("font-size", "13px");
        HorizontalLayout userBox = new HorizontalLayout(roleIcon, user);
        userBox.setSpacing(false);
        userBox.getStyle().set("gap", "6px");
        userBox.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
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
        HorizontalLayout right = new HorizontalLayout(userBox, logout);
        right.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.addAndExpand(new Span()); // スペーサー
        navbar.add(right);
        navbar.getStyle().set("padding", "0 16px");
        addToNavbar(navbar);

        // --- 左ドロワー（共通・画面設計 §94 / モックアップ準拠） ---
        // 各項目は絵文字ではなく統一アイコン（VaadinIcon）＋文字の行リンク。
        // CSS の .drawer-link で選択中（router-link-active）に苔色＋左差し色を出す。
        VerticalLayout drawer = new VerticalLayout(
                navLink(VaadinIcon.NEWSPAPER, "タイムライン", TimelineView.class),
                navLink(VaadinIcon.TAGS, "テーマ管理", ThemeView.class),
                navLink(VaadinIcon.BELL, "LINE通知設定", NotificationSettingsView.class));
        drawer.setSpacing(false);
        drawer.setPadding(true);
        drawer.getStyle().set("gap", "2px");

        // --- 管理グループ（admin のみ・SC-06〜09。メニュー非表示＋到達拒否の二重防御・BD-SC-00-06） ---
        if (CurrentUser.isAdmin()) {
            Span adminLabel = new Span("管理（admin のみ）");
            adminLabel.getStyle().set("font-size", "11px").set("color", "var(--lumo-secondary-text-color)")
                    .set("margin-top", "8px").set("padding", "0 10px");
            drawer.add(new Hr(), adminLabel,
                    navLink(VaadinIcon.GLOBE, "情報源マスタ", SourceMasterView.class),
                    navLink(VaadinIcon.CHART, "管理ダッシュボード", AdminDashboardView.class),
                    navLink(VaadinIcon.RECORDS, "実行ログ", ExecutionLogView.class),
                    navLink(VaadinIcon.USERS, "利用者管理", UserAdminView.class));
        }
        addToDrawer(drawer);
    }

    /** ドロワーの1項目。統一アイコン＋文字の行リンク（絵文字を使わない）。 */
    private RouterLink navLink(VaadinIcon icon, String text, Class<? extends Component> view) {
        RouterLink link = new RouterLink(text, view);
        link.addClassName("drawer-link");
        Icon ic = icon.create();
        ic.addClassName("nav-ic");
        link.addComponentAsFirst(ic);
        return link;
    }
}
