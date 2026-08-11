package com.example.aggregator.domain.notify;

/**
 * LINE 通知のポート（DD-CLS-17・BD-IF-00-03）。<b>実装は infrastructure</b>（line-bot-sdk-java）で、DI で
 * 差し替える。通知処理では <b>LLM を一切呼ばない</b>（要約は収集時に生成済み・CLAUDE.md §5）。
 *
 * <p>ドメインは LINE SDK の型に依存しない（{@link NotificationBundle} という自前 DTO で受け渡す）。
 * これにより「差し替え」「テストでのモック化」が容易になる（依存性逆転）。
 */
public interface LineNotifier {

    /** カルーセル1吹き出しを push し、結果を分類して返す。例外は投げず {@link PushOutcome} で表現する。 */
    PushOutcome push(NotificationBundle bundle);
}
