package com.example.aggregator.domain.llm;

import java.util.Optional;

/**
 * LLM 構造化のポート（DD-CLS-16・BD-IF-00-03）。<b>実装は infrastructure</b> に置き、DI で差し替える
 * （ROME/パーサーで埋まらないときのフォールバック。将来のモデル/ベンダ変更に備えて interface に切る）。
 *
 * <p>戻り値を {@link Optional} にする理由: 「予算切れ・APIキー未設定・失敗・不正JSON」など<b>構造化できない
 * 場合が正常系として起こりうる</b>。その場合は空を返し、呼び出し側は RSS 由来の値のまま登録を続ける
 * （収集は止めない・障害分離 NFR-10）。予算判定と usage 記録は実装側に閉じる。
 */
public interface LlmStructurer {

    /** 構造化できたら結果を返す。予算切れ・失敗・未設定なら {@link Optional#empty()}。 */
    Optional<StructuredArticle> structure(ExtractedText input);

    /**
     * LLM が実際に利用可能か（APIキー設定済みで呼び出す構成か）。既定 true。
     * 呼び出し側は、これが false のときは<b>記事本文の取得（重い処理）を省く</b>ために使う。
     * NoOp 実装は false を返す。
     */
    default boolean isEnabled() { return true; }
}
