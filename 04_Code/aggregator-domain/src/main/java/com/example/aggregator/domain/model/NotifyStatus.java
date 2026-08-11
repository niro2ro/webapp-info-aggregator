package com.example.aggregator.domain.model;

/**
 * LINE 送信結果の分類（notification_logs.status・smallint コード値）。外部IF §3.4／例外・リトライ §4 と対応。
 *
 * <p>設計書の表では AuthFailed と Blocked が共に「③」と記されているが、コード値は一意である必要があるため
 * 実装では <b>Blocked=4</b> を割り当てる（設計反映メモ: 意味は別物・処理も「友だち追加案内」で分岐しうる）。
 *
 * <ul>
 *   <li>{@code retryable()} … 同一実行内で自動リトライしてよいか（一時障害・レート制限のみ）</li>
 *   <li>{@code resendNext()} … 未通知のまま次回起動で再送するか（＝ArticleNotifications を作らない）</li>
 *   <li>{@code giveUp()} … 打ち切り（ArticleNotifications=GaveUp・再送しない）</li>
 * </ul>
 */
public enum NotifyStatus {
    SUCCESS(0),
    TEMP_ERROR(1),
    RATE_LIMITED(2),
    AUTH_FAILED(3),
    BLOCKED(4),
    FORMAT_ERROR(5),
    TIMEOUT(6),
    GAVE_UP(9);

    private final short code;
    NotifyStatus(int code) { this.code = (short) code; }
    public short code() { return code; }

    public boolean isSuccess() { return this == SUCCESS; }

    /** 同一実行内で自動リトライして良い分類（指数バックオフ）。 */
    public boolean retryable() { return this == TEMP_ERROR || this == RATE_LIMITED; }

    /** 打ち切り（不具合確定 or 5日超過）。ArticleNotifications=GaveUp にする。 */
    public boolean giveUp() { return this == FORMAT_ERROR || this == GAVE_UP; }

    public static NotifyStatus fromCode(short code) {
        for (NotifyStatus v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の NotifyStatus コード: " + code);
    }
}
