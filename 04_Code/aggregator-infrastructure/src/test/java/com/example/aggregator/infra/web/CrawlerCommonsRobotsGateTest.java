package com.example.aggregator.infra.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aggregator.domain.collect.HttpFetcher;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** robotsゲートの判定と日次キャッシュを、HttpFetcher をダブルにして検証する（ネットワーク不要）。 */
class CrawlerCommonsRobotsGateTest {

    private static final String UA = "Aggregator/0.1 (contact: you@example.com)";

    private CrawlerCommonsRobotsGate gate(HttpFetcher http) {
        return new CrawlerCommonsRobotsGate(http, UA);
    }

    @Test
    @DisplayName("Disallow: /private をブロック、それ以外は許可")
    void disallowRule() {
        HttpFetcher http = url -> "User-agent: *\nDisallow: /private\n";
        var g = gate(http);
        assertThat(g.isAllowed("https://ex.com/public/a")).isTrue();
        assertThat(g.isAllowed("https://ex.com/private/x")).isFalse();
    }

    @Test
    @DisplayName("robotsが404（無い）＝全許可")
    void notFoundAllowsAll() {
        HttpFetcher http = url -> { throw new HttpStatusException(404, "not found"); };
        assertThat(gate(http).isAllowed("https://ex.com/anything")).isTrue();
    }

    @Test
    @DisplayName("robots取得が一時エラー＝安全側で当日スキップ（不許可）")
    void transientErrorDisallows() {
        HttpFetcher http = url -> { throw new RuntimeException("timeout"); };
        assertThat(gate(http).isAllowed("https://ex.com/anything")).isFalse();
    }

    @Test
    @DisplayName("同一ホストの robots.txt は日次キャッシュで1回だけ取得")
    void dailyCache() {
        AtomicInteger calls = new AtomicInteger();
        HttpFetcher http = url -> { calls.incrementAndGet(); return "User-agent: *\nDisallow:\n"; };
        var g = gate(http);
        g.isAllowed("https://ex.com/a");
        g.isAllowed("https://ex.com/b");
        g.isAllowed("https://ex.com/c");
        assertThat(calls.get()).isEqualTo(1);   // robots.txt 取得は1回のみ
    }
}
