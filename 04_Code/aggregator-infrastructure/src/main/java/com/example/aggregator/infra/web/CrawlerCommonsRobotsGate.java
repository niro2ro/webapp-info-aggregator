package com.example.aggregator.infra.web;

import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.collect.RobotsGate;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * robots.txt ゲートの実装（DD-CLS-15）。crawler-commons で解析し、<b>ホスト単位で日次キャッシュ</b>する
 * （同日同ホストの robots.txt は1回だけ取得）。自前解析はしない（BD-IF-01-01）。
 *
 * <p>判定方針（BD-BATCH-C-03）: robots が「404/無い」＝許可扱い、robots 取得エラー（タイムアウト等）＝
 * 安全側で<b>当日スキップ（不許可）</b>。
 */
@Component
public class CrawlerCommonsRobotsGate implements RobotsGate {

    private static final Logger log = LoggerFactory.getLogger(CrawlerCommonsRobotsGate.class);

    private final HttpFetcher http;
    private final String robotName;   // robots.txt の User-agent 行と突合する識別子
    private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public CrawlerCommonsRobotsGate(HttpFetcher http, @Value("${app.user-agent}") String userAgent) {
        this.http = http;
        // UA 先頭のプロダクトトークン（例 "Aggregator/0.1 (...)" → "Aggregator"）を突合名に使う。
        this.robotName = userAgent.split("[/ ]", 2)[0];
    }

    @Override
    public boolean isAllowed(String url) {
        URI uri = URI.create(url);
        String host = uri.getScheme() + "://" + uri.getHost();
        LocalDate today = LocalDate.now();
        Entry entry = cache.get(host);
        if (entry == null || !entry.day.equals(today)) {
            entry = new Entry(today, loadRules(host));
            cache.put(host, entry);
        }
        return entry.rules.isAllowed(url);
    }

    private BaseRobotRules loadRules(String host) {
        String robotsUrl = host + "/robots.txt";
        try {
            String body = http.get(robotsUrl);
            return parser.parseContent(robotsUrl, body.getBytes(StandardCharsets.UTF_8),
                    "text/plain", robotName);
        } catch (HttpStatusException e) {
            if (e.isClientError()) {
                // 404 等＝robots が無い→全許可（設計の「無い＝許可扱い」）。
                return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
            }
            log.warn("[robots] 取得でエラー status={} host={} → 当日スキップ", e.getStatus(), host);
            return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_NONE);
        } catch (RuntimeException e) {
            // タイムアウト・接続断など＝安全側で当日スキップ（不許可）。
            log.warn("[robots] 取得エラー host={} → 当日スキップ: {}", host, e.getMessage());
            return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_NONE);
        }
    }

    private record Entry(LocalDate day, BaseRobotRules rules) {}
}
