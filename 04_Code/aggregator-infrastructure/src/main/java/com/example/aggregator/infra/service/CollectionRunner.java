package com.example.aggregator.infra.service;

import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.collect.RobotsGate;
import com.example.aggregator.domain.model.CrawlLogEntity;
import com.example.aggregator.domain.model.CrawlStatus;
import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.persistence.CrawlLogRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 実サイト収集のオーケストレーション（BD-BATCH-C・DD-CLS-01）。規約ゲートを通った情報源だけを対象に、
 * robots ゲート → HTTP 取得 → RSS 解析 → 取り込み（{@link CollectionService}）→ CrawlLog 記録 を
 * <b>情報源単位で独立</b>に実行する（1情報源の失敗が他を止めない・障害分離 NFR-10）。
 *
 * <p>取り込み側（正規化・ハッシュ・冪等・要約・テーマ突合・LLMフォールバック）は既存の
 * {@link CollectionService} が担う。ここは「どの情報源を・マナーを守って・どう取得して渡すか」に責務を絞る。
 */
@Service
public class CollectionRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectionRunner.class);

    private final SourceRepository sources;
    private final RobotsGate robotsGate;
    private final HttpFetcher http;
    private final RomeFeedFetcher feedFetcher;
    private final CollectionService collectionService;
    private final CrawlLogRepository crawlLogs;

    public CollectionRunner(SourceRepository sources, RobotsGate robotsGate, HttpFetcher http,
                            RomeFeedFetcher feedFetcher, CollectionService collectionService,
                            CrawlLogRepository crawlLogs) {
        this.sources = sources;
        this.robotsGate = robotsGate;
        this.http = http;
        this.feedFetcher = feedFetcher;
        this.collectionService = collectionService;
        this.crawlLogs = crawlLogs;
    }

    public record RunResult(int sources, int succeeded, int failed, int totalFetched, int totalRegistered) {}

    /** 収集本体。規約ゲート（is_active=true AND terms_reviewed=true）を満たす情報源のみループする。 */
    public RunResult run() {
        List<SourceEntity> targets = sources.findByActiveTrueAndTermsReviewedTrue();
        int ok = 0, ng = 0, fetched = 0, registered = 0;
        log.info("[収集] 対象情報源 {} 件", targets.size());
        for (SourceEntity source : targets) {
            CrawlLogEntity crawlLog = new CrawlLogEntity(source.getId(), Instant.now());
            try {
                CollectionService.IngestResult r = collectFrom(source);
                fetched += r.total();
                registered += r.registered();
                source.setLastFetchedAt(Instant.now());
                sources.save(source);
                crawlLog.finish(CrawlStatus.SUCCESS, r.total(), r.registered(), null);
                ok++;
            } catch (RobotsDisallowedException e) {
                // robots 不許可/取得エラー＝当日スキップ。全体は止めない（情報扱い）。
                crawlLog.finish(CrawlStatus.PARTIAL_ERROR, 0, 0, e.getMessage());
                ng++;
                log.info("[収集] robotsでスキップ source={} : {}", source.getName(), e.getMessage());
            } catch (RuntimeException e) {
                // 取得/解析の失敗はこの情報源だけ失敗にして次へ（障害分離・BD-BATCH-C-14）。
                crawlLog.finish(CrawlStatus.FAILED, 0, 0, e.toString());
                ng++;
                log.warn("[収集] source={} で失敗。次へ継続: {}", source.getName(), e.toString());
            }
            crawlLogs.save(crawlLog);
        }
        log.info("[収集] 完了 成功={} 失敗={} 取得計={} 新規計={}", ok, ng, fetched, registered);
        return new RunResult(targets.size(), ok, ng, fetched, registered);
    }

    /** 1情報源からの取得〜取り込み。取得方式に応じて分岐（現状 RSS を配線。専用パーサー/LLM 情報源は後続）。 */
    private CollectionService.IngestResult collectFrom(SourceEntity source) {
        if (source.getFetchType() != FetchType.RSS) {
            throw new IllegalStateException("未対応の取得方式: " + source.getFetchType() + "（専用パーサー/LLM情報源は後続）");
        }
        if (!robotsGate.isAllowed(source.getUrl())) {
            throw new RobotsDisallowedException("robots.txt により不許可、または robots 取得エラー: " + source.getUrl());
        }
        String xml = http.get(source.getUrl());          // UA/タイムアウト/リトライ/同一ホスト間隔は fetcher が担保
        List<RawItem> items = feedFetcher.parse(xml);     // ROME で解析（形式解釈はライブラリに委譲）
        return collectionService.ingest(source, items);   // 正規化→冪等→要約→突合→(必要時)LLM は既存に委譲
    }

    /** robots 不許可を分離して扱うための内部例外。 */
    private static final class RobotsDisallowedException extends RuntimeException {
        RobotsDisallowedException(String message) { super(message); }
    }
}
