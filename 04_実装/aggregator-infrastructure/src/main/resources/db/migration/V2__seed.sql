-- V2__seed.sql — 初期データ（開発用シード）
-- 利用者2名（admin/user）と第一陣情報源4サイト（いずれも規約未確認＝収集対象外）。
-- ※admin_pin_hash は NULL。PIN は後続 Phase で管理画面（SC-09）から設定する。
--   （PINハッシュ等の秘密相当値はシードに直書きしない。）
-- ※RSS URL・robots・規約は Phase 1/2 着手時に実地確認し、規約OKで terms_reviewed=true にする（FR-02-12）。

INSERT INTO users (display_name, role, notify_enabled) VALUES
    ('管理者アカウント', 9, true),
    ('ひろP',           1, true);

INSERT INTO sources (name, url, fetch_type, is_active, terms_reviewed, robots_respect) VALUES
    ('MANTANWEB',     'https://mantan-web.jp/',   1, true, false, true),
    ('HOBBY Watch',   'https://hobby.watch.impress.co.jp/', 1, true, false, true),
    ('Gamer',         'https://www.gamer.ne.jp/', 1, true, false, true),
    ('電撃ホビーウェブ', 'https://hobby.dengeki.com/', 1, true, false, true);
