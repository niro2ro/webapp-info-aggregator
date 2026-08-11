package com.example.aggregator.domain.llm;

import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;
import com.example.aggregator.domain.model.EventDatePrecision;
import java.time.LocalDate;
import java.util.Optional;

/**
 * LLM 構造化の出力（DD-CLS-27・外部IF §2.2 の JSON に対応）。
 *
 * <p>null になりうる項目（発生日など）は {@link Optional} のアクセサを添えて「無いことがある」ことを
 * 型で表現する（呼び出し側の null チェック漏れを防ぐ）。本文は保存しない方針のため、ここに持つのは
 * <b>自作要約</b>のみ（§9・権利配慮）。
 */
public record StructuredArticle(
        String title,
        Category category,
        LocalDate eventDate,
        String eventDateText,
        EventDatePrecision eventDatePrecision,
        EventDateKind eventDateKind,
        String location,
        String summary) {

    public Optional<LocalDate> eventDateOpt() { return Optional.ofNullable(eventDate); }
    public Optional<String> eventDateTextOpt() { return Optional.ofNullable(eventDateText); }
    public Optional<EventDateKind> eventDateKindOpt() { return Optional.ofNullable(eventDateKind); }
    public Optional<String> locationOpt() { return Optional.ofNullable(location); }
    public Optional<String> summaryOpt() { return Optional.ofNullable(summary); }
}
