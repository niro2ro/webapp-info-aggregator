-- V4__article_published_at.sql
-- 「記事の掲載日(published_at)」を「実イベント日(event_date=発売日/開催日/放送日)」から分離する。
--   event_date … 記事を読んで分かる実際の日付（LLM/パーサーが埋める。不明なら NULL）
--   published_at … 記事が配信された日（RSS の日付）＝「記事発生日/掲載日」
--   created_at … 収集した日（既存）
-- ※既存行の移行は行わない（過去の event_date が実イベント日か配信日か判別できないため）。再収集で埋まる。

ALTER TABLE articles ADD COLUMN published_at timestamptz;
CREATE INDEX ix_articles_published_at ON articles(published_at);

-- タイムライン整列キーを「実イベント日 → 掲載日 → 収集日」の代表日に見直す。
-- timestamptz は固定ゾーン(UTC)へ変換してから date 化（AT TIME ZONE 'UTC' は immutable）。
DROP INDEX IF EXISTS ix_articles_timeline;
CREATE INDEX ix_articles_timeline ON articles ((COALESCE(
    event_date,
    (published_at AT TIME ZONE 'UTC')::date,
    (created_at  AT TIME ZONE 'UTC')::date)));
