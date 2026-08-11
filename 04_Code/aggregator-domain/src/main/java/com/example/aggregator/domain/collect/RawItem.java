package com.example.aggregator.domain.collect;

import java.time.Instant;
import java.util.Optional;

/**
 * 取得直後の生データ（RSS/HTML パーサーの出力を統一した DTO・DD-CLS-25）。
 * {@code record} は不変で equals/hashCode 自動生成のため DTO に適する。null は Optional で表現する。
 */
public record RawItem(
        String title,
        String url,
        Instant publishedAt,
        String description,
        String categoryHint) {

    public Optional<Instant> publishedAtOpt() { return Optional.ofNullable(publishedAt); }
    public Optional<String> descriptionOpt() { return Optional.ofNullable(description); }
}
