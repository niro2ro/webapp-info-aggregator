package com.example.aggregator.domain.model;

/** 情報源の取得方式（FetchType 1:Rss / 2:HtmlParser / 3:Llm）。取得順のフォールバックに使う。 */
public enum FetchType {
    RSS(1), HTML_PARSER(2), LLM(3);

    private final short code;
    FetchType(int code) { this.code = (short) code; }
    public short code() { return code; }
    public static FetchType fromCode(short code) {
        for (FetchType v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の FetchType コード: " + code);
    }
}
