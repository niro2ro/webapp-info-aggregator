package com.example.aggregator.domain.llm;

/**
 * LLM 1回呼び出しのトークン使用量（DD-CLS-03・外部IF §2.3）。
 * コスト（マイクロ円）は単価が可変のため、使用量とは分けて {@code LlmBudgetGuard} 側で算出する。
 */
public record LlmUsage(String model, int inputTokens, int outputTokens) {

    public static LlmUsage zero(String model) { return new LlmUsage(model, 0, 0); }
}
