package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.persistence.SourceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 情報源マスタの編集: 3つのブール（有効/規約確認済/robots）が反映され、規約確認日も連動することを検証する。 */
@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock SourceRepository sources;

    private SourceService service() {
        return new SourceService(sources);
    }

    /** seed 相当: 有効・未確認・robots尊重の情報源。 */
    private SourceEntity seeded() {
        SourceEntity s = new SourceEntity("MANTANWEB", "https://mantan-web.jp/", FetchType.RSS);
        s.setActive(true);
        s.setTermsReviewed(false);
        s.setRobotsRespect(true);
        return s;
    }

    @Test
    @DisplayName("未確認→規約確認済にチェックすると true になり、確認日が記録される")
    void reviewedTurnsOn() {
        SourceEntity s = seeded();
        when(sources.findById(1L)).thenReturn(Optional.of(s));
        when(sources.save(any())).thenAnswer(i -> i.getArgument(0));

        service().update(1L, "MANTANWEB",
                "https://news.google.com/rss/search?q=site:mantan-web.jp", FetchType.RSS,
                true,   // active
                true,   // termsReviewed（今回チェック）
                "規約OK確認済み",
                true);  // robotsRespect

        assertThat(s.isTermsReviewed()).isTrue();
        assertThat(s.getTermsReviewedAt()).isNotNull();     // 確認日が連動記録される
        assertThat(s.isActive()).isTrue();
        assertThat(s.isRobotsRespect()).isTrue();
        assertThat(s.getUrl()).isEqualTo("https://news.google.com/rss/search?q=site:mantan-web.jp");
    }

    @Test
    @DisplayName("有効/robots のチェックを外すと false で保存される")
    void togglesActiveAndRobotsOff() {
        SourceEntity s = seeded();
        when(sources.findById(1L)).thenReturn(Optional.of(s));
        when(sources.save(any())).thenAnswer(i -> i.getArgument(0));

        service().update(1L, "MANTANWEB", "https://mantan-web.jp/", FetchType.RSS,
                false,  // active を外す
                false,  // termsReviewed 未確認のまま
                null,
                false); // robots を外す

        assertThat(s.isActive()).isFalse();
        assertThat(s.isRobotsRespect()).isFalse();
        assertThat(s.isTermsReviewed()).isFalse();
    }

    @Test
    @DisplayName("規約確認済→未確認に戻すと確認日はクリアされる")
    void reviewedTurnsOffClearsDate() {
        SourceEntity s = seeded();
        s.setTermsReviewed(true);   // すでに確認済
        assertThat(s.getTermsReviewedAt()).isNotNull();
        when(sources.findById(1L)).thenReturn(Optional.of(s));
        when(sources.save(any())).thenAnswer(i -> i.getArgument(0));

        service().update(1L, "MANTANWEB", "https://mantan-web.jp/", FetchType.RSS,
                true, false, null, true);   // termsReviewed を外す

        assertThat(s.isTermsReviewed()).isFalse();
        assertThat(s.getTermsReviewedAt()).isNull();
    }
}
