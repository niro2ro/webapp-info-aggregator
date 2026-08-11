package com.example.aggregator.infra.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 関連の設定（app.llm.*）。単価・上限・モデルを<b>コードに直書きせず設定で外部化</b>する
 * （BD-IF-00-02・DD-CFG）。単価は改定されうるため、確定単価を設定値として持ち、着手時／改定時に見直す。
 *
 * <p>{@code @ConfigurationProperties} を使う理由: 関連する設定値を型付きの1クラスにまとめられ、
 * {@code @Value} を項目ごとに書くより見通しが良い（Spring 推奨）。{@code @Component} で Bean 登録する。
 */
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** LLM 構造化を有効化するか。false（既定）なら NoOp 実装が使われ、APIキー無しでも起動する。 */
    private boolean enabled = false;

    /** 使用モデル（外部IF §2.1・コスト重視で Sonnet 既定）。着手時に公式のモデルIDを確認。 */
    private String model = "claude-sonnet-5";

    /** 応答上限トークン。構造化 JSON は短いので小さめ。 */
    private int maxTokens = 1024;

    /** 当月のハードキャップ（円・NFR-06）。 */
    private int monthlyBudgetJpy = 500;

    /** 実際に止める安全マージン（上限のこの割合に達したら呼ばない・BD-IF-02-01）。 */
    private double budgetMargin = 0.9;

    /** 入力1トークンあたりの概算単価（マイクロ円）。既定は Sonnet 目安（$3/1M・150円/$換算）。要確認。 */
    private long inputMicroJpyPerToken = 450;

    /** 出力1トークンあたりの概算単価（マイクロ円）。既定は Sonnet 目安（$15/1M・150円/$換算）。要確認。 */
    private long outputMicroJpyPerToken = 2250;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getMonthlyBudgetJpy() { return monthlyBudgetJpy; }
    public void setMonthlyBudgetJpy(int monthlyBudgetJpy) { this.monthlyBudgetJpy = monthlyBudgetJpy; }
    public double getBudgetMargin() { return budgetMargin; }
    public void setBudgetMargin(double budgetMargin) { this.budgetMargin = budgetMargin; }
    public long getInputMicroJpyPerToken() { return inputMicroJpyPerToken; }
    public void setInputMicroJpyPerToken(long v) { this.inputMicroJpyPerToken = v; }
    public long getOutputMicroJpyPerToken() { return outputMicroJpyPerToken; }
    public void setOutputMicroJpyPerToken(long v) { this.outputMicroJpyPerToken = v; }

    /** 上限（マイクロ円）にマージンを掛けた実効しきい値。 */
    public long effectiveCapMicroJpy() {
        return (long) (monthlyBudgetJpy * 1_000_000L * budgetMargin);
    }

    /** 使用量からマイクロ円コストを算出（整数演算で丸め誤差を避ける）。 */
    public long estimateMicroJpy(int inputTokens, int outputTokens) {
        return inputTokens * inputMicroJpyPerToken + outputTokens * outputMicroJpyPerToken;
    }
}
