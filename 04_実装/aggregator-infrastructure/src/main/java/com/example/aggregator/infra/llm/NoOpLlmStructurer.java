package com.example.aggregator.infra.llm;

import com.example.aggregator.domain.llm.ExtractedText;
import com.example.aggregator.domain.llm.LlmStructurer;
import com.example.aggregator.domain.llm.StructuredArticle;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM 無効時の既定実装（{@code app.llm.enabled=false} または未設定）。常に空を返す＝<b>APIキー無しでも起動でき、
 * 収集は RSS 由来の値のまま進む</b>。開発機・CI・協力者テストではこれで動かし、本番でキーを入れて有効化する。
 *
 * <p>{@code matchIfMissing = true} により、設定が無い環境でもこの Bean が選ばれる（安全側の既定）。
 * {@link ClaudeLlmStructurer} とは有効/無効で排他になるようにしている。
 */
@Component
@ConditionalOnProperty(prefix = "app.llm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpLlmStructurer implements LlmStructurer {

    @Override
    public Optional<StructuredArticle> structure(ExtractedText input) {
        return Optional.empty();
    }
}
