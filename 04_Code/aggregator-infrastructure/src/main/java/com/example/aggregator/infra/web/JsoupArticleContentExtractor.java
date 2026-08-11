package com.example.aggregator.infra.web;

import com.example.aggregator.domain.collect.ArticleContentExtractor;
import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.collect.RobotsGate;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 記事本文の抽出（jsoup）。<b>LLM に発売日等を読ませるための入力テキストを作る</b>（外部IF §2.2）。
 *
 * <p>手順: robots ゲート → HTTP 取得（UA/タイムアウト/同一ホスト間隔は {@link HttpFetcher} が担保）→ jsoup で
 * script/style/nav/header/footer 等の非本文を除去してテキスト化 → コスト抑制のため上限文字数で切り詰め。
 * <b>本文は保存しない</b>（抽出用の一時利用のみ・§9）。robots 不許可・取得失敗は empty（呼び出し側は RSS 要約へ）。
 */
@Component
public class JsoupArticleContentExtractor implements ArticleContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsoupArticleContentExtractor.class);

    private final HttpFetcher http;
    private final RobotsGate robotsGate;
    private final CollectProperties props;

    public JsoupArticleContentExtractor(HttpFetcher http, RobotsGate robotsGate, CollectProperties props) {
        this.http = http;
        this.robotsGate = robotsGate;
        this.props = props;
    }

    @Override
    public Optional<String> extract(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        try {
            if (!robotsGate.isAllowed(url)) {
                log.info("[本文抽出] robotsで不許可のため本文取得スキップ: {}", url);
                return Optional.empty();
            }
            String html = http.get(url);
            Document doc = Jsoup.parse(html, url);
            // 非本文ノードを除去して読みやすいテキストにする。
            doc.select("script, style, noscript, nav, header, footer, form, aside").remove();
            String text = doc.body() != null ? doc.body().text() : doc.text();
            if (text == null || text.isBlank()) return Optional.empty();
            String trimmed = text.length() > props.getMaxBodyChars()
                    ? text.substring(0, props.getMaxBodyChars()) : text;
            return Optional.of(trimmed);
        } catch (RuntimeException e) {
            log.info("[本文抽出] 取得/解析に失敗（RSS要約にフォールバック）: {} : {}", url, e.toString());
            return Optional.empty();
        }
    }
}
