package com.example.aggregator.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aggregator.domain.model.EventDatePrecision;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ルールベース発売日抽出（LLM不使用）の代表パターンを検証する。 */
class EventDateExtractorTest {

    private final EventDateExtractor ext = new EventDateExtractor();

    /** 参照時刻（年推定の基準）を JST の指定日から作る。 */
    private Instant ref(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(TimeZones.JST).toInstant();
    }

    @Test
    @DisplayName("年つき正確日: 2026年9月18日 → EXACT")
    void ymdKanji() {
        Optional<EventDateExtractor.Extracted> r = ext.extract("○○ 2026年9月18日 発売決定", ref(2026, 1, 1));
        assertThat(r).isPresent();
        assertThat(r.get().date()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(r.get().precision()).isEqualTo(EventDatePrecision.EXACT);
    }

    @Test
    @DisplayName("スラッシュ日付: 2026/9/18 → EXACT")
    void ymdSlash() {
        assertThat(ext.extract("発売日 2026/9/18", ref(2026, 1, 1)).get().date())
                .isEqualTo(LocalDate.of(2026, 9, 18));
    }

    @Test
    @DisplayName("年なし: 9月18日 は参照日の年で解釈（近い将来）")
    void mdInfersYear() {
        assertThat(ext.extract("9月18日 発売", ref(2026, 8, 1)).get().date())
                .isEqualTo(LocalDate.of(2026, 9, 18));
    }

    @Test
    @DisplayName("年なしで過去すぎる日付は翌年に繰り上げ（発表はこれから）")
    void mdRollsToNextYear() {
        // 参照が12月、対象が1月10日 → 同年1月は過去すぎ → 翌年
        assertThat(ext.extract("1月10日 発売予定", ref(2026, 12, 1)).get().date())
                .isEqualTo(LocalDate.of(2027, 1, 10));
    }

    @Test
    @DisplayName("上旬/中旬/下旬 → MONTH（代表日 5/15/25）")
    void jun() {
        assertThat(ext.extract("9月中旬 発売", ref(2026, 1, 1)).get().date())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(ext.extract("2026年9月下旬 発売", ref(2026, 1, 1)).get().precision())
                .isEqualTo(EventDatePrecision.MONTH);
    }

    @Test
    @DisplayName("年つき月まで: 2026年9月 → MONTH（代表日=1日）")
    void ym() {
        EventDateExtractor.Extracted e = ext.extract("2026年9月 発売予定", ref(2026, 1, 1)).get();
        assertThat(e.date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(e.precision()).isEqualTo(EventDatePrecision.MONTH);
    }

    @Test
    @DisplayName("季節: 2026年春 → SEASON（代表月=3）")
    void season() {
        EventDateExtractor.Extracted e = ext.extract("2026年春 放送開始", ref(2026, 1, 1)).get();
        assertThat(e.date()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(e.precision()).isEqualTo(EventDatePrecision.SEASON);
    }

    @Test
    @DisplayName("月のみ: 発売等の語があれば拾う／無ければ拾わない（誤検出抑制）")
    void monthOnlyNeedsCue() {
        assertThat(ext.extract("9月 発売", ref(2026, 1, 1))).isPresent();
        assertThat(ext.extract("特集：好きな作品 全9作", ref(2026, 1, 1))).isEmpty();  // 「9作」は月ではない
        assertThat(ext.extract("9月号のお知らせ", ref(2026, 1, 1))).isEmpty();          // 発売等の語が無い
    }

    @Test
    @DisplayName("日付表現が無ければ empty")
    void noDate() {
        assertThat(ext.extract("新商品の情報が公開", ref(2026, 1, 1))).isEmpty();
    }

    @Test
    @DisplayName("不正な日付（2026年13月40日）は採用しない")
    void invalidDate() {
        assertThat(ext.extract("2026年13月40日", ref(2026, 1, 1))).isEmpty();
    }
}
