package com.example.aggregator.domain.model;

/**
 * 情報源の取得方式。<b>RSS のみ</b>（専用パーサー(jsoup)・LLM取得は廃止）。
 *
 * <p>※ここでいう「取得方式」は記事を<b>どう取ってくるか</b>（入口）の話。取得後に発売日・カテゴリを
 * 本文から補完する LLM 構造化（{@code LLM_ENABLED}）は別工程で、取得方式が RSS でも動く。
 */
public enum FetchType {
    RSS(1);

    private final short code;
    FetchType(int code) { this.code = (short) code; }
    public short code() { return code; }

    /**
     * DBコード→enum。RSS のみのため、旧データの 2(専用パーサー)・3(LLM) も含め、未知コードは
     * すべて {@link #RSS} にフォールバックする（廃止後も既存行を壊さない）。
     */
    public static FetchType fromCode(short code) {
        for (FetchType v : values()) if (v.code == code) return v;
        return RSS;
    }
}
