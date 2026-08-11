package com.example.aggregator.web.security;

import com.example.aggregator.domain.model.UserRole;
import com.vaadin.flow.server.VaadinSession;
import java.io.Serializable;
import java.util.Optional;

/**
 * ログイン中の利用者をブラウザセッションに保持する（Phase 5 の軽量認証）。
 *
 * <p>Vaadin はサーバー側ステートフル UI のため、ログイン状態は {@link VaadinSession} 属性に置く。
 * ここに「利用者ID・表示名・ロール」だけを持ち、パスワード等の秘密は保持しない。画面はこの値を見て
 * 自分の担当利用者を決める（従来の固定 USER_ID を置き換える）。認可の最終判断はルートガードで行う。
 */
public final class CurrentUser {

    private CurrentUser() {}

    /** セッションに入れる最小限の利用者情報（不変・直列化可能）。 */
    public record Info(Long id, String displayName, UserRole role) implements Serializable {
        public boolean isAdmin() { return role == UserRole.ADMIN; }
    }

    public static void set(Info info) {
        VaadinSession.getCurrent().setAttribute(Info.class, info);
    }

    public static Optional<Info> get() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? Optional.empty()
                : Optional.ofNullable(session.getAttribute(Info.class));
    }

    /** ログイン中の利用者ID。未ログインなら例外（ガード通過後の画面から呼ぶ前提）。 */
    public static Long requireId() {
        return get().map(Info::id)
                .orElseThrow(() -> new IllegalStateException("未ログインです。"));
    }

    public static boolean isLoggedIn() { return get().isPresent(); }

    public static boolean isAdmin() { return get().map(Info::isAdmin).orElse(false); }

    /** ログアウト（セッションを破棄して再ログインを促す）。 */
    public static void logout() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(Info.class, null);
            session.getSession().invalidate();
        }
    }
}
