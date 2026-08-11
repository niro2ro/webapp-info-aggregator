package com.example.aggregator.domain.model;

/**
 * LLM 呼び出しの結果ステータス（llm_usage_logs.status・smallint コード値）。
 * 0:Success（構造化成功）/ 1:Failed（API/通信の失敗）/ 2:FormatError（JSON不正・スキップ）。
 * ※予算切れで「呼ばなかった」場合は呼び出し自体が無いためログを残さない（BD-IF-02-01）。
 */
public enum LlmCallStatus {
    SUCCESS(0), FAILED(1), FORMAT_ERROR(2);

    private final short code;
    LlmCallStatus(int code) { this.code = (short) code; }
    public short code() { return code; }
    public static LlmCallStatus fromCode(short code) {
        for (LlmCallStatus v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の LlmCallStatus コード: " + code);
    }
}
