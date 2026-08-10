package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;

/**
 * 一覧の検索条件（FR-04-01/02/03）。null の条件は「絞り込まない」を意味する。
 * 並び順は列挙で持ち、SQL の ORDER BY は実装側でホワイトリスト化する（SQLインジェクション防止）。
 */
public record ArticleQuery(
        Long userId,
        String text,          // 部分一致（pg_trgm・タイトル/要約）。null/空は無視
        Category category,    // カテゴリ絞り込み。null は無視
        EventDateKind kind,   // 発生日種別絞り込み。null は無視
        Long themeId,         // テーマ絞り込み（マッチ記事のみ）。null は無視
        boolean unreadOnly,   // 未読のみ
        Sort sort,
        int limit) {

    public enum Sort { EVENT_DESC, EVENT_ASC, RELEASE, COLLECTED_DESC }

    public static ArticleQuery defaults(Long userId) {
        return new ArticleQuery(userId, null, null, null, null, false, Sort.EVENT_DESC, 50);
    }
}
