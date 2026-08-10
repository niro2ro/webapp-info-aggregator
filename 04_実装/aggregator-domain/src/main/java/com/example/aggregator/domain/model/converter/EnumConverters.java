package com.example.aggregator.domain.model.converter;

import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.CrawlStatus;
import com.example.aggregator.domain.model.EventDateKind;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.domain.model.FetchType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * enum ↔ smallint コード値の変換（DD-DAO-09）。
 *
 * <p>{@code @Enumerated(ORDINAL)}（列挙順に暗黙依存）を避け、各 enum の {@code code()} を明示保存する。
 * {@code autoApply = true} により、該当型のフィールドすべてに自動適用される（エンティティ側に
 * {@code @Convert} を書かなくてよい）。Spring Boot のエンティティスキャンが本 Converter 群を
 * 管理クラスとして登録する。
 */
public final class EnumConverters {

    private EnumConverters() {}

    @Converter(autoApply = true)
    public static class CategoryConverter implements AttributeConverter<Category, Short> {
        public Short convertToDatabaseColumn(Category a) { return a == null ? null : a.code(); }
        public Category convertToEntityAttribute(Short db) { return db == null ? null : Category.fromCode(db); }
    }

    @Converter(autoApply = true)
    public static class FetchTypeConverter implements AttributeConverter<FetchType, Short> {
        public Short convertToDatabaseColumn(FetchType a) { return a == null ? null : a.code(); }
        public FetchType convertToEntityAttribute(Short db) { return db == null ? null : FetchType.fromCode(db); }
    }

    @Converter(autoApply = true)
    public static class EventDatePrecisionConverter implements AttributeConverter<EventDatePrecision, Short> {
        public Short convertToDatabaseColumn(EventDatePrecision a) { return a == null ? null : a.code(); }
        public EventDatePrecision convertToEntityAttribute(Short db) { return db == null ? null : EventDatePrecision.fromCode(db); }
    }

    @Converter(autoApply = true)
    public static class EventDateKindConverter implements AttributeConverter<EventDateKind, Short> {
        public Short convertToDatabaseColumn(EventDateKind a) { return a == null ? null : a.code(); }
        public EventDateKind convertToEntityAttribute(Short db) { return db == null ? null : EventDateKind.fromCode(db); }
    }

    @Converter(autoApply = true)
    public static class CrawlStatusConverter implements AttributeConverter<CrawlStatus, Short> {
        public Short convertToDatabaseColumn(CrawlStatus a) { return a == null ? null : a.code(); }
        public CrawlStatus convertToEntityAttribute(Short db) { return db == null ? null : CrawlStatus.fromCode(db); }
    }
}
