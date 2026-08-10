package com.example.aggregator.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web アプリのエントリポイント（詳細設計 DD-ARC-07）。
 *
 * <p>{@code scanBasePackages} をルートパッケージにして、将来 infrastructure 側に置く
 * Bean（リポジトリ実装・外部APIクライアント）もコンポーネントスキャン対象にする。
 * Phase 0 ではエンティティ／リポジトリはまだ無く、DataSource 接続と Flyway 実行の確認に留める。
 */
@SpringBootApplication(scanBasePackages = "com.example.aggregator")
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
