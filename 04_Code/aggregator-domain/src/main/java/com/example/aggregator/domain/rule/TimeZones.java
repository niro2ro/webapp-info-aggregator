package com.example.aggregator.domain.rule;

import java.time.ZoneId;

/**
 * ゾーンIDを一箇所に集約する（NFR-08）。コード各所に "Asia/Tokyo" を直書きしない。
 * 保存は常に UTC、表示・「同日/当月」判定のみ JST に変換する。
 */
public final class TimeZones {
    public static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    public static final ZoneId UTC = ZoneId.of("UTC");
    private TimeZones() {}
}
