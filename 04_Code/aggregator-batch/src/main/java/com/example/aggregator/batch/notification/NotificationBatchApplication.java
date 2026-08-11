package com.example.aggregator.batch.notification;

import com.example.aggregator.infra.llm.ClaudeLlmStructurer;
import com.example.aggregator.infra.llm.LlmBudgetGuard;
import com.example.aggregator.infra.llm.LlmProperties;
import com.example.aggregator.infra.llm.NoOpLlmStructurer;
import com.example.aggregator.infra.service.CollectionService;
import com.example.aggregator.infra.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 通知バッチのエントリポイント（詳細設計 DD-ARC-09）。収集とは別プロセスでワンショット実行する。
 *
 * <p><b>通知処理では LLM を一切呼ばない</b>（CLAUDE.md §5・DD-DI-06）。これを「配線レベル」で保証するため、
 * このプロセスのコンポーネントスキャンから <b>LLM 関連 Bean と収集サービスを除外</b>する
 * （{@link ComponentScan} の {@code excludeFilters}）。＝通知プロセスの Bean グラフに LlmStructurer が存在しない。
 *
 * <p>{@code @SpringBootApplication} は既定で自クラスのパッケージを走査するが、ここでは明示的な
 * {@code @ComponentScan} で対象パッケージ（com.example.aggregator 全体）と除外を上書き指定する。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.example.aggregator",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {CollectionService.class, LlmBudgetGuard.class,
                           NoOpLlmStructurer.class, ClaudeLlmStructurer.class, LlmProperties.class}))
public class NotificationBatchApplication {

    private static final Logger log = LoggerFactory.getLogger(NotificationBatchApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(NotificationBatchApplication.class, args);
    }

    /**
     * ワンショット処理。未通知抽出→お気に入り絞込→通数ガード→LINE 送信→冪等記録を1回行い終了する。
     * NoOp 実装（トークン未設定）ではログのみで Delivered 記録まで通り、冪等・分類の挙動を確認できる。
     */
    @Bean
    CommandLineRunner notificationRunner(NotificationService notificationService) {
        return args -> {
            NotificationService.NotificationResult r = notificationService.run();
            log.info("[通知バッチ] 完了: 対象={} 通知={} 記事={} 通数={}",
                    r.usersProcessed(), r.usersNotified(), r.articlesNotified(), r.messagesSent());
        };
    }
}
