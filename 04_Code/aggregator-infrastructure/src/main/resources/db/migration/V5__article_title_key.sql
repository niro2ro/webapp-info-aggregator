-- V5__article_title_key.sql
-- 同一タイトルの記事を1件に集約する（別サイト＝別URLで来た同じ記事の重複を防ぐ・FR-02-09 簡易版）。
--   title_key … 重複判定用の正規化タイトル（末尾の「 - 媒体名」を除去）。アプリ側 TitleKey と同じ考え方。

ALTER TABLE articles ADD COLUMN title_key text;

-- 既存行を後方互換で埋める（末尾の「 - 媒体名」除去＋前後trim）。
UPDATE articles SET title_key = btrim(regexp_replace(title, ' - [^-]*$', ''));

-- 既存の重複（同じ title_key）は、最も古い1件（最小id）だけ残して削除する。
-- 関連（matches/read_states/bookmarks/article_notifications）は ON DELETE CASCADE で連動削除される。
DELETE FROM articles a
USING articles b
WHERE a.title_key = b.title_key
  AND a.title_key IS NOT NULL AND a.title_key <> ''
  AND a.id > b.id;

CREATE INDEX ix_articles_title_key ON articles(title_key);
