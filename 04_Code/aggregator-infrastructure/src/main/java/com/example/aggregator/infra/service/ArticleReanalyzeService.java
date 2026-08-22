package com.example.aggregator.infra.service;

import com.example.aggregator.domain.collect.ArticleContentExtractor;
import com.example.aggregator.domain.llm.ExtractedText;
import com.example.aggregator.domain.llm.LlmStructurer;
import com.example.aggregator.domain.llm.StructuredArticle;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.rule.EventDateExtractor;
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
    private final EventDateExtractor dateExtractor;

    public ArticleReanalyzeService(ArticleRepository articles, ArticleContentExtractor extractor,
                                   LlmStructurer llm, EventDateExtractor dateExtractor) {
        this.articles = articles;
        this.extractor = extractor;
        this.llm = llm;
        this.dateExtractor = dateExtractor;
    }

    public record Result(boolean llmEnabled, int scanned, int updated, int failed) {}

    /** 実イベント日が空の記事を最大 limit 件、まずルールで、ダメなら（LLM有効時）LLMで埋める（管理画面の一括穴埋め）。 */
    @Transactional
    public Result reanalyzeMissingEventDates(int limit) {
        return fill(articles.findByEventDateIsNullOrderByCreatedAtDesc(PageRequest.of(0, limit)));
    }

    /**
     * 指定利用者の有効テーマ記事のうち発売日未設定を最大 limit 件だけ補完する（タイムラインで「発売日順」を
     * 選んだ時だけ呼ぶ・オンデマンド）。まずルールベース（無料・即時）、取れなければ LLM（有効時のみ）。
     * 上限で切るため1回で全ては埋めない。
     */
    @Transactional
    public Result reanalyzeForUserThemes(Long userId, int limit) {
        return fill(articles.findUserThemedMissingEventDate(userId, PageRequest.of(0, limit)));
    }

    /**
     * 対象記事群の発売日を埋める共通処理（C案）。① まずルールベースでタイトル＋要約から日付を拾う（無料・即時）。
     * ② 取れなければ LLM が有効なときだけ本文取得→LLM構造化で補う。ルールで埋まれば LLM は呼ばない＝最小コスト。
     */
    private Result fill(List<ArticleEntity> targets) {
        int updated = 0, failed = 0;
        for (ArticleEntity a : targets) {
            try {
                // ① ルールベース（LLM不使用）
                String text = (a.getTitle() == null ? "" : a.getTitle())
                        + " " + (a.getSummary() == null ? "" : a.getSummary());
                var ruled = dateExtractor.extract(text, a.getPublishedAt());
                if (ruled.isPresent()) {
                    a.enrichEventDate(ruled.get().date(), ruled.get().precision(), null, ruled.get().text(), null);
                    articles.save(a);
                    updated++;
                    continue;   // ルールで埋まったら LLM は呼ばない
                }
                // ② LLM フォールバック（有効なときだけ）
                if (!llm.isEnabled()) continue;
                String body = extractor.extract(a.getUrl())
                        .orElseGet(() -> a.getSummary() != null ? a.getSummary() : a.getTitle());
                Optional<StructuredArticle> r = llm.structure(new ExtractedText(a.getTitle(), a.getUrl(), body));
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
        log.info("[再解析] 対象={} 更新={} 失敗={} (LLM有効={})", targets.size(), updated, failed, llm.isEnabled());
        return new Result(llm.isEnabled(), targets.size(), updated, failed);
    }
}
