package com.example.aggregator.batch.collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 収集バッチのエントリポイント（詳細設計 DD-ARC-08）。Web と別プロセスでワンショット実行し終了する。
 *
 * <p>Phase 0 は骨格のみ（DB 接続確認）。実際の収集（RSS→専用パーサー→LLM のフォールバック・冪等・
 * robots/規約ゲート）は Phase 1/2 で実装する（バッチ設計書 BD-BATCH-C）。
 * このプロセスには <b>通知経路の LLM 呼び出しを一切置かない</b>のと対に、通知側にも LLM を置かない
 * （配線での §5 保証・DD-DI-06）。
 */
@SpringBootApplication(scanBasePackages = "com.example.aggregator")
public class CollectionBatchApplication {

    private static final Logger log = LoggerFactory.getLogger(CollectionBatchApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CollectionBatchApplication.class, args);
    }

    /** ワンショット処理。CommandLineRunner は起動後に1回実行され、完了するとプロセスは終了する。 */
    @Bean
    CommandLineRunner collectionRunner() {
        return args -> log.info("[収集バッチ] Phase 0 骨格: 起動確認のみ（収集本体は Phase 1/2 で実装）");
    }
}
