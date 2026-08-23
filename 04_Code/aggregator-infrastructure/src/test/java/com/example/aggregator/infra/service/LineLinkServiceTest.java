package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 合言葉連携: 発行→一致で連携／不一致・二重使用は連携しない、を検証する。 */
class LineLinkServiceTest {

    private final UserService users = mock(UserService.class);
    private final LineLinkService svc = new LineLinkService(users);

    @Test
    @DisplayName("発行した合言葉をWebhookが渡すと、その利用者に line_user_id を紐付ける")
    void linksOnMatchingCode() {
        String code = svc.issueCode(2L);
        assertThat(code).hasSize(6);

        boolean linked = svc.linkFromMessage("U1234567890abcdef", code);

        assertThat(linked).isTrue();
        verify(users).linkLine(2L, "U1234567890abcdef");   // DB更新はUserServiceに委譲
    }

    @Test
    @DisplayName("合言葉に一致しない本文は連携しない")
    void ignoresNonCodeText() {
        svc.issueCode(2L);
        boolean linked = svc.linkFromMessage("Uxxxx", "こんにちは");
        assertThat(linked).isFalse();
        verify(users, never()).linkLine(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("同じ合言葉は一度使うと無効（二重連携しない）")
    void codeIsSingleUse() {
        String code = svc.issueCode(2L);
        assertThat(svc.linkFromMessage("U1", code)).isTrue();
        assertThat(svc.linkFromMessage("U2", code)).isFalse();   // 2回目は対象外
    }

    @Test
    @DisplayName("前後の空白は無視して一致（トークのコピペ対策）")
    void trimsWhitespace() {
        String code = svc.issueCode(2L);
        assertThat(svc.linkFromMessage("U1", "  " + code + " ")).isTrue();
    }

    @Test
    @DisplayName("再発行すると古い合言葉は無効になる（1人1つ）")
    void reissueInvalidatesOld() {
        String first = svc.issueCode(2L);
        String second = svc.issueCode(2L);
        assertThat(second).isNotEqualTo(first);
        assertThat(svc.linkFromMessage("U1", first)).isFalse();   // 古い方は無効
        assertThat(svc.linkFromMessage("U1", second)).isTrue();
    }
}
