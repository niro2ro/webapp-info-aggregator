package com.example.aggregator.batch.collection;

import com.example.aggregator.batch.notification.NotificationBatchApplication;
import com.example.aggregator.infra.notify.LineBotNotifier;
import com.example.aggregator.infra.notify.LineProperties;
import com.example.aggregator.infra.notify.NoOpLineNotifier;
import com.example.aggregator.infra.service.CollectionRunner;
import com.example.aggregator.infra.service.NotificationCountService;
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
 * 収集バッチのエントリポイント（詳細設計 DD-ARC-08）。Web と別プロセスでワンショット実行し終了する。
 *
 * <p>収集本体（RSS 収集・ルール抽出・冪等・robots/規約ゲート）のオーケストレーションを担う。
 * <b>通知系 Bean は配線から除外</b>する（収集プロセスに LINE 送信経路を持ち込まない・DD-DI-06 の対称）。
 *
 * <p>同居する通知側 {@link NotificationBatchApplication} も {@code @Configuration} として取り込まれ
 * その {@code @Bean}（{@link NotificationService} を要求）まで登録されると、除外済みの通知サービスが
 * 見つからず起動失敗する。よって <b>兄弟のエントリポイントも除外</b>する。加えて、この設定クラスの
 * {@code @Bean} メソッド名は {@code @Service CollectionRunner} の既定 Bean 名（collectionRunner）と
 * 衝突しないよう {@code collectionJobRunner} にしている。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.example.aggregator",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {NotificationBatchApplication.class,
                           NotificationService.class, NotificationCountService.class,
                           LineBotNotifier.class, NoOpLineNotifier.class, LineProperties.class}))
public class CollectionBatchApplication {

    private static final Logger log = LoggerFactory.getLogger(CollectionBatchApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CollectionBatchApplication.class, args);
    }

    /**
     * ワンショット処理。CommandLineRunner は起動後に1回実行され、完了するとプロセスは終了する。
     * 規約確認済（terms_reviewed=true）の情報源から実収集する（対象0件なら何もせず終了）。
     */
    @Bean
    CommandLineRunner collectionJobRunner(CollectionRunner collectionRunner) {
        return args -> {
            CollectionRunner.RunResult r = collectionRunner.run();
            log.info("[収集バッチ] 完了: 情報源{} 成功{} 失敗{} 取得{} 新規{}",
                    r.sources(), r.succeeded(), r.failed(), r.totalFetched(), r.totalRegistered());
        };
    }
}
