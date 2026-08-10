package com.example.aggregator.web.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;

/**
 * 共通レイアウト（AppLayout: 上部バー＋左ドロワー・BD-SC-00-01）。
 *
 * <p>各画面（{@code @Route(layout = MainLayout.class)}）はこのレイアウトの中に表示される。Phase 1 では
 * ドロワーに「タイムライン」「テーマ管理」を置く（ログイン/管理系は Phase 5）。画面クラスは業務ロジックを
 * 持たず、サービスを呼ぶだけに保つ（DD-CLS-11）。
 */
public class MainLayout extends AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);

        // --- 上部バー ---
        Span title = new Span("📚 テーマ別最新情報アグリゲーター");
        title.addClassName("app-title");
        Span user = new Span("ひろP");
        user.getStyle().set("color", "#f2ecdd").set("font-size", "13px");

        HorizontalLayout navbar = new HorizontalLayout(new DrawerToggle(), title);
        navbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.setWidthFull();
        navbar.setSpacing(true);
        navbar.setPadding(false);
        HorizontalLayout right = new HorizontalLayout(user);
        right.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        navbar.addAndExpand(new Span()); // スペーサー
        navbar.add(right);
        navbar.getStyle().set("padding", "0 16px");
        addToNavbar(navbar);

        // --- 左ドロワー ---
        VerticalLayout drawer = new VerticalLayout(
                new RouterLink("🗒  タイムライン", TimelineView.class),
                new RouterLink("🏷  テーマ管理", ThemeView.class),
                new RouterLink("⭐  お気に入り", FavoritesView.class));
        drawer.setSpacing(true);
        drawer.setPadding(true);
        addToDrawer(drawer);
    }
}
