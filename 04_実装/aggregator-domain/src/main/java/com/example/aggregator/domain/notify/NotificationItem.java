package com.example.aggregator.domain.notify;

import java.time.LocalDate;

/**
 * 通知カルーセル1件ぶんの表示データ（DD-CLS-29 の構成要素）。記事本文は持たず、自作要約と元URLのみ
 * （権利配慮・§9）。日付は代表日（無ければ null）。
 */
public record NotificationItem(
        Long articleId,
        String title,
        String url,
        String summary,
        LocalDate eventDate) {}
