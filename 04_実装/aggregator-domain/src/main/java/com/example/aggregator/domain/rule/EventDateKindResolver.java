package com.example.aggregator.domain.rule;

import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;

/**
 * 発生日種別（EventDateKind）の判定（FR-02-06・ビジネスルール §1）。
 *
 * <p>コストを抑えるため段階的に決める:
 * <ol>
 *   <li>カテゴリ既定マッピング（グッズ/カプセルトイ/ゲーム/漫画→発売日、アニメ→放送日、
 *       イベント→開催日、ゲームセンター/その他→その他）</li>
 *   <li>原文キーワード補正（発売→発売日 / 開催→開催日 / 放送・配信→放送日 / 予約・受付→受付開始）</li>
 * </ol>
 * ③ LLM を呼ぶ記事のみ、LLM 出力の種別を優先採用する（本クラスは①②のみ・種別のために LLM を呼ばない）。
 */
public class EventDateKindResolver {

    public EventDateKind resolve(Category category, String originalText) {
        EventDateKind base = byCategory(category);
        return byKeyword(originalText).orElse(base);
    }

    private EventDateKind byCategory(Category c) {
        if (c == null) return EventDateKind.OTHER;
        return switch (c) {
            case GOODS, CAPSULE_TOY, GAME, MANGA -> EventDateKind.RELEASE;
            case ANIME -> EventDateKind.BROADCAST;
            case EVENT -> EventDateKind.EVENT;
            case ARCADE, OTHER -> EventDateKind.OTHER;
        };
    }

    private java.util.Optional<EventDateKind> byKeyword(String text) {
        if (text == null || text.isBlank()) return java.util.Optional.empty();
        String t = text;
        if (t.contains("発売")) return java.util.Optional.of(EventDateKind.RELEASE);
        if (t.contains("開催")) return java.util.Optional.of(EventDateKind.EVENT);
        if (t.contains("放送") || t.contains("配信")) return java.util.Optional.of(EventDateKind.BROADCAST);
        if (t.contains("予約") || t.contains("受付")) return java.util.Optional.of(EventDateKind.ACCEPT_START);
        return java.util.Optional.empty();
    }
}
