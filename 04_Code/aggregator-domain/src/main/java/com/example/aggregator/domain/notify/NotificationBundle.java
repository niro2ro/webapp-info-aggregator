package com.example.aggregator.domain.notify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 1利用者への通知1回ぶんの束（DD-CLS-29）。対象記事集合を1つの Flex カルーセル（1吹き出し）に集約する。
 *
 * <p><b>冪等キー（BD-IF-03-04/05）</b>: 「利用者 × 対象記事集合」で決まる決定的な UUID を持つ。届いたか不明な
 * 再送（タイムアウト等）で同じキーを送れば、LINE 側で二重配信を防げる。記事IDを昇順連結した文字列から
 * {@link UUID#nameUUIDFromBytes(byte[])}（名前ベース UUID）で生成する＝同じ集合なら毎回同じキー。
 */
public record NotificationBundle(
        Long userId,
        String lineUserId,
        List<NotificationItem> items,
        UUID retryKey) {

    /** カルーセルのバブル上限（着手時に公式確認。おおむね12件・BD-IF-03-02）。 */
    public static final int MAX_BUBBLES = 12;

    /** 記事集合から決定的な冪等キーを組み立てて束を作る。 */
    public static NotificationBundle of(Long userId, String lineUserId, List<NotificationItem> items) {
        String seed = userId + ":" + items.stream()
                .map(NotificationItem::articleId)
                .sorted()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        UUID key = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        return new NotificationBundle(userId, lineUserId, items, key);
    }

    public int articleCount() { return items.size(); }
}
