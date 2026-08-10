package com.example.aggregator.batch.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 通知バッチのエントリポイント（詳細設計 DD-ARC-09）。収集とは別プロセスでワンショット実行する。
 *
 * <p>Phase 0 は骨格のみ。実際の通知（未通知抽出→お気に入り絞込→カルーセル→LINE 送信→冪等記録・
 * NotifyStatus 分類の再送/打ち切り）は Phase 4 で実装する（バッチ設計書 BD-BATCH-N）。
 * <b>通知処理では LLM を一切呼ばない</b>（CLAUDE.md §5・DD-DI-06）。
 */
@SpringBootApplication(scanBasePackages = "com.example.aggregator")
public class NotificationBatchApplication {

    private static final Logger log = LoggerFactory.getLogger(NotificationBatchApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(NotificationBatchApplication.class, args);
    }

    @Bean
    CommandLineRunner notificationRunner() {
        return args -> log.info("[通知バッチ] Phase 0 骨格: 起動確認のみ（通知本体は Phase 4 で実装）");
    }
}
