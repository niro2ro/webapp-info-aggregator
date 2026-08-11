package com.example.aggregator.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 発生日種別の判定（FR-02-06・§1）。①カテゴリ既定 → ②原文キーワードで上書き、の順序を検証する。 */
class EventDateKindResolverTest {

    private final EventDateKindResolver r = new EventDateKindResolver();

    @Test
    @DisplayName("①カテゴリ既定: グッズ→発売、アニメ→放送、イベント→開催")
    void byCategoryDefault() {
        assertThat(r.resolve(Category.GOODS, "")).isEqualTo(EventDateKind.RELEASE);
        assertThat(r.resolve(Category.ANIME, "")).isEqualTo(EventDateKind.BROADCAST);
        assertThat(r.resolve(Category.EVENT, "")).isEqualTo(EventDateKind.EVENT);
        assertThat(r.resolve(Category.OTHER, "")).isEqualTo(EventDateKind.OTHER);
    }

    @Test
    @DisplayName("②原文キーワードはカテゴリ既定より優先される")
    void keywordOverridesCategory() {
        // アニメ（既定=放送）でも本文に「発売」があれば発売日
        assertThat(r.resolve(Category.ANIME, "Blu-ray発売決定")).isEqualTo(EventDateKind.RELEASE);
        // その他（既定=その他）でも「開催」で開催日
        assertThat(r.resolve(Category.OTHER, "コラボカフェ開催")).isEqualTo(EventDateKind.EVENT);
        // 「予約」「受付」は受付開始
        assertThat(r.resolve(Category.GOODS, "予約受付スタート")).isEqualTo(EventDateKind.ACCEPT_START);
        // 「配信」は放送日扱い
        assertThat(r.resolve(Category.GAME, "配信開始")).isEqualTo(EventDateKind.BROADCAST);
    }

    @Test
    @DisplayName("キーワードが無ければカテゴリ既定に戻る / null 安全")
    void fallbackAndNullSafe() {
        assertThat(r.resolve(Category.MANGA, "新刊情報")).isEqualTo(EventDateKind.RELEASE);
        assertThat(r.resolve(Category.MANGA, null)).isEqualTo(EventDateKind.RELEASE);
        assertThat(r.resolve(null, null)).isEqualTo(EventDateKind.OTHER);
    }
}
