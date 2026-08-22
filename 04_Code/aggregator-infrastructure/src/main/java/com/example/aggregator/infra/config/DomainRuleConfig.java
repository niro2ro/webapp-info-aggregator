package com.example.aggregator.infra.config;

import com.example.aggregator.domain.rule.EventDateExtractor;
import com.example.aggregator.domain.rule.EventDateKindResolver;
import com.example.aggregator.domain.rule.UrlHasher;
import com.example.aggregator.domain.rule.UrlNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ドメインルール（状態を持たない純粋クラス）を Bean 化する。
 *
 * <p>domain は Spring 非依存にしておきたい（{@code @Component} を付けない）ので、Bean 登録は
 * infrastructure 側の Configuration に集約する。状態を持たないため singleton で安全（DD-DI-03）。
 */
@Configuration
public class DomainRuleConfig {

    @Bean
    UrlNormalizer urlNormalizer() { return new UrlNormalizer(); }

    @Bean
    UrlHasher urlHasher() { return new UrlHasher(); }

    @Bean
    EventDateKindResolver eventDateKindResolver() { return new EventDateKindResolver(); }

    @Bean
    EventDateExtractor eventDateExtractor() { return new EventDateExtractor(); }
}
