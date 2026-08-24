package com.example.aggregator.batch.notification;

import com.example.aggregator.batch.collection.CollectionBatchApplication;
import com.example.aggregator.infra.llm.ClaudeLlmStructurer;
import com.example.aggregator.infra.llm.NoOpLlmStructurer;
import com.example.aggregator.infra.service.ArticleReanalyzeService;
import com.example.aggregator.infra.service.CollectionRunner;
import com.example.aggregator.infra.service.CollectionService;
import com.example.aggregator.infra.service.NotificationService;
import com.example.aggregator.infra.service.ThemeSearchCollector;
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
 * このプロセスのコンポーネントスキャンから <b>LlmStructurer の実装（{@link NoOpLlmStructurer} /
 * {@link ClaudeLlmStructurer}）と収集オーケストレータを除外</b>する（{@link ComponentScan} の
 * {@code excludeFilters}）。＝通知プロセスの Bean グラフに LlmStructurer が存在しない＝LLM 呼び出し経路が無い。
 *
 * <p>一方で {@code LlmProperties}（設定値 POJO）や {@code LlmBudgetGuard}／{@code CostService}
 * （当月コストの集計のみ・LLM は呼ばない）は<b>除外しない</b>。これらは設定/会計用で、除外すると
 * それらを注入する {@code CostService} 等が依存欠落で起動失敗する。LLM を呼ばない保証は
 * 「LlmStructurer 実装を除外する」ことで十分に得られるため、巻き添え除外を避け対象を最小化している。
 *
 * <p>{@code @SpringBootApplication} は既定で自クラスのパッケージを走査するが、ここでは明示的な
 * {@code @ComponentScan} で対象パッケージ（com.example.aggregator 全体）と除外を上書き指定する。
 *
 * <p><b>収集側エントリポイントの除外が必須</b>: このモジュールには収集用の
 * {@link CollectionBatchApplication} も同居する。{@code @SpringBootApplication} は内部的に
 * {@code @Configuration} でもあるため、com.example.aggregator を丸ごと走査すると収集側クラスが
 * 設定クラスとして取り込まれ、その {@code @Bean} メソッド（CommandLineRunner）まで登録されてしまう。
 * これは通知プロセスには不要なうえ、収集専用サービス（{@link CollectionRunner} 等＝{@code @Service}
 * の既定 Bean 名と {@code @Bean} メソッド名が衝突する）や、除外済みの {@link CollectionService} を
 * 要求して起動失敗（Bean 名重複／依存欠落）になる。そのため <b>兄弟のエントリポイントと収集専用サービスも
 * 併せて除外</b>し、通知プロセスの Bean グラフを通知系だけに閉じる。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.example.aggregator",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {CollectionBatchApplication.class,
                           CollectionRunner.class, ThemeSearchCollector.class, ArticleReanalyzeService.class,
                           CollectionService.class,
                           NoOpLlmStructurer.class, ClaudeLlmStructurer.class}))
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
