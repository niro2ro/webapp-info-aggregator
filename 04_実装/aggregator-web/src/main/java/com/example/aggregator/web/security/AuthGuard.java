package com.example.aggregator.web.security;

import com.example.aggregator.web.ui.LoginView;
import com.example.aggregator.web.ui.SignupView;
import com.example.aggregator.web.ui.TimelineView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * ルート認可（BD-SC-00-06・NFR-02）。全ての画面遷移の前に割り込み、次を強制する（サーバー側で判定）:
 *
 * <ul>
 *   <li>未ログインで公開画面（ログイン/新規登録）以外へ来たら → ログイン画面へ差し戻す</li>
 *   <li>{@link AdminOnly} 画面へ非 admin が来たら → タイムラインへ差し戻す（到達拒否）</li>
 * </ul>
 *
 * <p><b>なぜ画面ごとでなくここで一括判定するか</b>: 各ビューに認可コードを散らさず1箇所に集約でき、
 * 追加画面もマーカー（AdminOnly）だけで守れる。メニュー非表示は UX、到達拒否はこのガードが担保する
 * （二重防御）。{@code VaadinServiceInitListener} は Vaadin 起動時に UI ごとの遷移リスナを仕込む仕組み。
 */
@Component
public class AuthGuard implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit -> {
            UI ui = uiInit.getUI();
            ui.addBeforeEnterListener(this::beforeEnter);
        });
    }

    private void beforeEnter(BeforeEnterEvent event) {
        Class<?> target = event.getNavigationTarget();
        boolean isPublic = target == LoginView.class || target == SignupView.class;

        if (!CurrentUser.isLoggedIn()) {
            if (!isPublic) {
                event.forwardTo(LoginView.class);   // 未ログイン → ログインへ
            }
            return;
        }
        // ログイン済みで管理者専用画面に来たが admin でない → タイムラインへ
        if (AdminOnly.class.isAssignableFrom(target) && !CurrentUser.isAdmin()) {
            event.forwardTo(TimelineView.class);
        }
    }
}
