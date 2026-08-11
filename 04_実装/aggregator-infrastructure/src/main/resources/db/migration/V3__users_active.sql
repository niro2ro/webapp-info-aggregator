-- V3__users_active.sql — 利用者の有効フラグ（SC-01 の「有効な利用者一覧」・SC-09 の有効/無効切替）
-- 既存行は true（有効）で埋める。無効化した利用者はログイン一覧に出さない（論理削除的な運用）。
ALTER TABLE users ADD COLUMN active boolean NOT NULL DEFAULT true;
