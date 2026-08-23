package com.example.aggregator.web.ui;

import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.domain.model.NotifyStatus;
import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.domain.notify.LineNotifier;
import com.example.aggregator.domain.notify.NotificationBundle;
import com.example.aggregator.domain.notify.NotificationItem;
import com.example.aggregator.domain.notify.PushOutcome;
import com.example.aggregator.infra.notify.LineProperties;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.service.ArticleInteractionService;
import com.example.aggregator.infra.service.FavoriteService;
import com.example.aggregator.infra.service.LineLinkService;
import com.example.aggregator.infra.service.UserService;
import com.example.aggregator.web.security.CurrentUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.Map;

/**
 * LINE通知設定（SC-05・旧「お気に入り管理」を改称/拡張・Q26）。LINE通知に関わる本人設定を1画面に集約する。
 *
 * <ul>
 *   <li><b>上段＝LINEに繋ぐ設定</b>: line_user_id 登録／通知の全体ON/OFF／連携ステータス＋友だち追加案内</li>
 *   <li><b>下段＝何を通知するか</b>: テーマお気に入り／情報源お気に入り（各「お気に入り」＋「通知ON/OFF」）／ブックマーク</li>
 * </ul>
 * 通知が届く条件: ①お気に入り登録 ②その通知ON ③全体通知ON ④LINE ID登録済 ⑤通数枠あり（画面にも明示）。
 * 「お気に入り＝通知する／ブックマーク＝後で見るだけ（通知しない）」。
 */
@Route(value = "notify", layout = MainLayout.class)
@PageTitle("LINE通知設定 | アグリゲーター")
public class NotificationSettingsView extends VerticalLayout {

    private final Long userId = CurrentUser.get().map(CurrentUser.Info::id).orElse(-1L);

    private final ThemeRepository themes;
    private final SourceRepository sources;
    private final ArticleRepository articles;
    private final FavoriteService favorites;
    private final ArticleInteractionService interaction;
    private final UserService users;
    private final LineNotifier lineNotifier;
    private final LineProperties lineProps;
    private final LineLinkService lineLink;

    private final Grid<ThemeEntity> themeGrid = new Grid<>(ThemeEntity.class, false);
    private final Grid<SourceEntity> sourceGrid = new Grid<>(SourceEntity.class, false);
    private final Grid<ArticleEntity> bookmarkGrid = new Grid<>(ArticleEntity.class, false);

    public NotificationSettingsView(ThemeRepository themes, SourceRepository sources, ArticleRepository articles,
                                    FavoriteService favorites, ArticleInteractionService interaction,
                                    UserService users, LineNotifier lineNotifier, LineProperties lineProps,
                                    LineLinkService lineLink) {
        this.themes = themes;
        this.sources = sources;
        this.articles = articles;
        this.favorites = favorites;
        this.interaction = interaction;
        this.users = users;
        this.lineNotifier = lineNotifier;
        this.lineProps = lineProps;
        this.lineLink = lineLink;

        setSizeFull();
        setPadding(true);

        H2 title = new H2("LINE通知設定");
        title.addClassName("view-title");

        add(title, buildLineBlock(), buildTabs());
        refresh();
    }

    // ===== 上段: 自分のLINE連携（合言葉方式・userID入力不要／BD-SC-05-05〜08） =====
    private VerticalLayout buildLineBlock() {
        VerticalLayout block = new VerticalLayout();
        block.addClassName("line-link-card");
        block.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "12px").set("background", "var(--lumo-base-color)");
        block.setPadding(true);
        block.setSpacing(true);
        block.setWidthFull();

        UserEntity me = users.find(userId).orElse(null);

        H3 h = new H3("LINE連携（友だち追加＋合言葉）");
        h.getStyle().set("margin", "0");

        Span status = new Span();

        // --- 連携手順（未連携時に表示） ---
        Anchor addFriend = new Anchor("https://line.me/", "① 公式アカウントを友だち追加する");
        addFriend.setTarget("_blank");
        addFriend.getStyle().set("font-size", "14px").set("font-weight", "600");

        Span codeLabel = new Span();   // 発行した合言葉を大きく表示
        codeLabel.getStyle().set("font-size", "22px").set("font-weight", "700")
                .set("letter-spacing", "3px").set("font-family", "monospace");
        Span codeHint = new Span();
        codeHint.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");

        Button issue = new Button("② 合言葉を発行");
        issue.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button recheck = new Button("③ 連携状況を更新");
        recheck.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button unlink = new Button("連携解除");
        unlink.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        Button test = new Button("テスト送信");
        test.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        VerticalLayout steps = new VerticalLayout(addFriend,
                new HorizontalLayout(issue, recheck), codeLabel, codeHint);
        steps.setPadding(false);
        steps.setSpacing(false);

        // 連携状態で表示/活性を切り替える
        Runnable refreshState = () -> {
            UserEntity cur = users.find(userId).orElse(null);
            boolean linked = cur != null && cur.getLineUserId() != null && !cur.getLineUserId().isBlank();
            applyStatus(status, cur);
            steps.setVisible(!linked);        // 連携済みなら手順は隠す
            unlink.setEnabled(linked);
            test.setEnabled(linked);
            if (linked) { codeLabel.setText(""); codeHint.setText(""); }
        };

        issue.addClickListener(e -> {
            String code = lineLink.issueCode(userId);
            codeLabel.setText("合言葉：" + code);
            codeHint.setText("↑ この番号を、友だち追加した公式アカウントのトークにそのまま送ってください（10分間有効）。"
                    + "送ったら「③ 連携状況を更新」を押します。");
        });
        recheck.addClickListener(e -> {
            refreshState.run();
            UserEntity cur = users.find(userId).orElse(null);
            boolean linked = cur != null && cur.getLineUserId() != null && !cur.getLineUserId().isBlank();
            Notification.show(linked ? "連携が完了しました。" : "まだ連携が確認できません。合言葉を送ったか確認して、少し待って再度お試しください。",
                    4000, Notification.Position.MIDDLE);
        });
        unlink.addClickListener(e -> {
            users.unlinkLine(userId);
            refreshState.run();
            Notification.show("LINE連携を解除しました（通知先を削除）。");
        });
        test.addClickListener(e -> sendTest(status));

        HorizontalLayout linkedActions = new HorizontalLayout(unlink, test);
        linkedActions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        // 通知の全体ON/OFF（マスタスイッチ）。変更で即保存する。
        Checkbox notifyAll = new Checkbox("通知を受け取る（全体ON/OFF）");
        notifyAll.setValue(me == null || me.isNotifyEnabled());
        notifyAll.addValueChangeListener(e -> {
            users.setNotifyEnabled(userId, e.getValue());
            applyStatus(status, users.find(userId).orElse(null));
        });

        Span cond = new Span("通知が届く条件: ①お気に入り登録 ②その通知ON ③全体通知ON ④LINE連携済 ⑤通数枠あり");
        cond.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");
        Span webhookNote = new Span("※合言葉での自動連携には、アプリが公開URLで受信できること（Webhook）が必要です。"
                + "自宅PCでは Cloudflare Tunnel 等、恒久運用は VPS（Phase 6）。未設定の間は下の「手動で連携」で検証できます。");
        webhookNote.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "11px");

        // --- 開発/検証用: userID を手動入力（Webhook未設定でもテストできる逃げ道） ---
        TextField lineId = new TextField("LINEユーザーID（Uから始まる33文字）");
        lineId.setWidth("340px");
        if (me != null && me.getLineUserId() != null) lineId.setValue(me.getLineUserId());
        Button manualReg = new Button("手動で連携", e -> {
            try {
                users.linkLine(userId, lineId.getValue());
                refreshState.run();
                Notification.show("手動で連携しました。");
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });
        manualReg.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        HorizontalLayout manualRow = new HorizontalLayout(lineId, manualReg);
        manualRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        Details manual = new Details("開発/検証用: userID を手動で入力（Webhook未設定時）", manualRow);

        refreshState.run();   // 初期表示

        block.add(h, status, steps, linkedActions, notifyAll, cond, webhookNote, manual);
        return block;
    }

    /** 連携ステータス表示（簡易: ID・全体ONの有無から判定）。実送信の失敗表示は実行ログ/通知実績に基づく拡張余地。 */
    private void applyStatus(Span status, UserEntity me) {
        boolean hasId = me != null && me.getLineUserId() != null && !me.getLineUserId().isBlank();
        boolean on = me != null && me.isNotifyEnabled();
        String text;
        String color;
        if (!hasId) { text = "未連携（LINEユーザーID未登録 → 通知は届きません）"; color = "var(--lumo-error-text-color)"; }
        else if (!on) { text = "連携OK・ただし全体通知OFF（届きません）"; color = "var(--lumo-secondary-text-color)"; }
        else { text = "連携OK（通知が届きます）"; color = "#4e7d55"; }
        status.setText("連携ステータス: " + text);
        status.getStyle().set("color", color).set("font-size", "13px").set("font-weight", "600");
    }

    /** 登録済みのユーザーIDへテスト通知を1通送る（トークン/友だち追加/IDの確認用）。 */
    private void sendTest(Span status) {
        if (!lineProps.isEnabled()) {
            Notification.show("LINEが無効です。secrets.bat で LINE_ENABLED=true とアクセストークンを設定して再起動してください。",
                    6000, Notification.Position.MIDDLE);
            return;
        }
        UserEntity me = users.find(userId).orElse(null);
        if (me == null || me.getLineUserId() == null || me.getLineUserId().isBlank()) {
            Notification.show("先にLINEユーザーIDを登録してください。");
            return;
        }
        NotificationItem item = new NotificationItem(0L, "【テスト】通知テスト",
                "https://line.me/", "このメッセージが届けばLINE連携は成功です。", null);
        NotificationBundle bundle = NotificationBundle.of(userId, me.getLineUserId(), java.util.List.of(item));
        PushOutcome outcome = lineNotifier.push(bundle);   // 実送信は例外を投げず結果で返る
        Notification.show(testMessage(outcome.status()), 6000, Notification.Position.MIDDLE);
        applyStatus(status, me);
    }

    /** テスト送信結果を利用者向けメッセージに変換（外部IF §3.4 の分類に対応）。 */
    private static String testMessage(NotifyStatus s) {
        return switch (s) {
            case SUCCESS -> "テスト送信しました。LINEを確認してください（届くまで数秒）。";
            case AUTH_FAILED -> "失敗: トークンが無効です。Developers Consoleでアクセストークンを再確認してください。";
            case BLOCKED -> "失敗: 友だち未追加/ブロックの可能性。botを友だち追加してください。";
            case RATE_LIMITED -> "失敗: レート制限です。少し待って再試行してください。";
            case TEMP_ERROR -> "失敗: 一時的な障害です。少し待って再試行してください。";
            case FORMAT_ERROR -> "失敗: 送信内容の形式エラー（不具合）。";
            case TIMEOUT -> "失敗: タイムアウト。再試行してください。";
            default -> "失敗: " + s;
        };
    }

    // ===== 下段: お気に入り／ブックマーク（タブ・BD-SC-05-01） =====
    private TabSheet buildTabs() {
        buildThemeGrid();
        buildSourceGrid();
        buildBookmarkGrid();
        Span note = new Span("お気に入り＝通知する／ブックマーク＝後で見るだけ（通知しません）");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px");

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("テーマお気に入り", themeGrid);
        tabs.add("情報源お気に入り", sourceGrid);
        tabs.add("ブックマーク", new VerticalLayout(note, bookmarkGrid));
        return tabs;
    }

    private void buildThemeGrid() {
        themeGrid.addColumn(ThemeEntity::getKeyword).setHeader("テーマ").setAutoWidth(true);
        themeGrid.addComponentColumn(t -> {
            Map<Long, Boolean> favs = favorites.themeFavorites(userId);
            boolean fav = favs.containsKey(t.getId());
            Button b = new Button(fav ? "★ お気に入り中" : "☆ お気に入り", e -> {
                favorites.toggleThemeFavorite(userId, t.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("お気に入り");
        themeGrid.addComponentColumn(t -> {
            Map<Long, Boolean> favs = favorites.themeFavorites(userId);
            if (!favs.containsKey(t.getId())) return new Span("—");
            Checkbox c = new Checkbox("通知", favs.get(t.getId()));
            c.addValueChangeListener(e -> favorites.setThemeNotify(userId, t.getId(), e.getValue()));
            return c;
        }).setHeader("通知ON/OFF");
        themeGrid.setWidthFull();
    }

    private void buildSourceGrid() {
        sourceGrid.addColumn(SourceEntity::getName).setHeader("情報源").setAutoWidth(true);
        sourceGrid.addComponentColumn(s -> {
            Map<Long, Boolean> favs = favorites.sourceFavorites(userId);
            boolean fav = favs.containsKey(s.getId());
            Button b = new Button(fav ? "★ お気に入り中" : "☆ お気に入り", e -> {
                favorites.toggleSourceFavorite(userId, s.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("お気に入り");
        sourceGrid.addComponentColumn(s -> {
            Map<Long, Boolean> favs = favorites.sourceFavorites(userId);
            if (!favs.containsKey(s.getId())) return new Span("—");
            Checkbox c = new Checkbox("通知", favs.get(s.getId()));
            c.addValueChangeListener(e -> favorites.setSourceNotify(userId, s.getId(), e.getValue()));
            return c;
        }).setHeader("通知ON/OFF");
        sourceGrid.setWidthFull();
    }

    private void buildBookmarkGrid() {
        bookmarkGrid.addColumn(ArticleEntity::getTitle).setHeader("記事（後で見る）").setAutoWidth(true);
        bookmarkGrid.addColumn(a -> a.getEventDate() != null ? a.getEventDate().toString() : "(日付不明)")
                .setHeader("発生日").setAutoWidth(true);
        bookmarkGrid.addComponentColumn(a -> {
            Button b = new Button("解除", e -> {
                interaction.toggleBookmark(userId, a.getId());
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return b;
        }).setHeader("操作");
        bookmarkGrid.setWidthFull();
    }

    private void refresh() {
        // テーマは自分の分だけ（アカウントごと）。情報源は共有だが内部の検索用ソース(active=false)は隠す。
        themeGrid.setItems(themes.findByUserIdAndActiveTrueOrderByKeyword(userId));
        sourceGrid.setItems(sources.findAll().stream().filter(SourceEntity::isActive).toList());
        bookmarkGrid.setItems(articles.findBookmarkedByUser(userId));
    }
}
