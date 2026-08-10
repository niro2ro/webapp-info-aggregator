package com.example.aggregator.infra.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * エンティティとリポジトリの探索範囲を明示する（DD-ARC-11）。
 *
 * <p>エンティティは domain（{@code domain.model}）、リポジトリは infrastructure（{@code infra.persistence}）
 * に分かれているため、起動アプリのメインパッケージ既定スキャンだけでは見つからない。ここで明示する。
 */
@Configuration
@EntityScan(basePackages = "com.example.aggregator.domain.model")
@EnableJpaRepositories(basePackages = "com.example.aggregator.infra.persistence")
public class PersistenceConfig {
}
