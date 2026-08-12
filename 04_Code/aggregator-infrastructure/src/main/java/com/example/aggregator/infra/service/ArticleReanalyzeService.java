package com.example.aggregator.infra.service;

import com.example.aggregator.domain.collect.ArticleContentExtractor;
import com.example.aggregator.domain.llm.ExtractedText;
import com.example.aggregator.domain.llm.LlmStructurer;
import com.example.aggregator.domain.llm.StructuredArticle;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.infra.persistence.ArticleRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 既存記事の再解析。<b>実イベント日(event_date)が未設定の記事だけ</b>を対象に、本文を取得して LLM で発売日等を
 * 抽出し、DB を更新する（取りこぼしの穴埋め）。通常収集は「同じURLはスキップ＝冪等」なので、LLM を後から
 * 有効化しても過去記事の発売日は埋まらない。それを補うための保守機能。
 *
 * <p>安全策: LLM 無効なら何もしない。1回の対象件数は上限を設ける（本文取得＋LLM呼び出しが件数ぶん走るため）。
 * コストは LLM 側の予算ガード（月500円）で頭打ちになる。
 */
@Service
public class ArticleReanalyzeService {

    private static final Logger log = LoggerFactory.getLogger(ArticleReanalyzeService.class);

    private final ArticleRepository articles;
    private final ArticleContentExtractor extractor;
    private final LlmStructurer llm;

    public ArticleReanalyzeService(ArticleRepository articles, ArticleContentExtractor extractor,
                                   LlmStructurer llm) {
        this.articles = articles;
        this.extractor = extractor;
        this.llm = llm;
    }

    public record Result(boolean llmEnabled, int scanned, int updated, int failed) {}

    /** 実イベント日が空の記事を最大 limit 件、LLM で再解析して発売日等を埋める。 */
    @Transactional
    public Result reanalyzeMissingEventDates(int limit) {
        if (!llm.isEnabled()) {
            return new Result(false, 0, 0, 0);   // LLM 無効: 何もしない（画面で案内）
        }
        List<ArticleEntity> targets = articles.findByEventDateIsNullOrderByCreatedAtDesc(PageRequest.of(0, limit));
        int updated = 0, failed = 0;
        for (ArticleEntity a : targets) {
            try {
                String text = extractor.extract(a.getUrl())
                        .orElseGet(() -> a.getSummary() != null ? a.getSummary() : a.getTitle());
                Optional<StructuredArticle> r = llm.structure(new ExtractedText(a.getTitle(), a.getUrl(), text));
                if (r.isPresent() && r.get().eventDate() != null) {
                    StructuredArticle sa = r.get();
                    a.enrichEventDate(sa.eventDate(), sa.eventDatePrecision(), sa.eventDateKind(),
                            sa.eventDateText(), sa.location());
                    articles.save(a);
                    updated++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("[再解析] article id={} で失敗: {}", a.getId(), e.toString());
            }
        }
        log.info("[再解析] 対象={} 更新={} 失敗={}", targets.size(), updated, failed);
        return new Result(true, targets.size(), updated, failed);
    }
}
