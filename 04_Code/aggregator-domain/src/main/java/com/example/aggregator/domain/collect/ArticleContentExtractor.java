package com.example.aggregator.domain.collect;

import java.util.Optional;

/**
 * 記事ページから本文テキストを抽出するポート（LLM 入力用・外部IF §2.2）。実装は infrastructure（HTTP取得＋jsoup）。
 *
 * <p>取得した本文は<b>保存しない</b>（LLM に発売日等を抽出させるための一時利用のみ・§9 権利配慮）。robots 不許可・
 * 取得失敗のときは {@link Optional#empty()} を返し、呼び出し側は RSS 要約にフォールバックする（収集は止めない）。
 */
public interface ArticleContentExtractor {

    /** 記事URLの本文テキスト（抽出・トリム済み）。取得できなければ empty。 */
    Optional<String> extract(String url);
}
