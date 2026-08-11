package com.example.aggregator.domain.notify;

import com.example.aggregator.domain.model.NotifyStatus;

/**
 * LINE push の結果（{@link NotifyStatus} と消費通数）。通知サービスはこれを見て、
 * 成功なら ArticleNotifications=Delivered＋通数加算、失敗なら分類に応じて再送/打ち切りを判断する。
 */
public record PushOutcome(NotifyStatus status, int messageCount) {

    /** 成功（カルーセル1吹き出し＝1通）。 */
    public static PushOutcome success() { return new PushOutcome(NotifyStatus.SUCCESS, 1); }

    /** 失敗（通数は消費しない扱い＝0）。 */
    public static PushOutcome failed(NotifyStatus status) { return new PushOutcome(status, 0); }
}
