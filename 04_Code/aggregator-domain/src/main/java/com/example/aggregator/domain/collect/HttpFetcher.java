package com.example.aggregator.domain.collect;

/**
 * HTTP 取得のポート（DD-CLS-14・HttpContentFetcher）。実装は infrastructure（タイムアウト・リトライ・
 * User-Agent・同一ホスト間隔などの収集マナーを内包）。ドメインは「URLを渡すと本文文字列が返る」ことだけを知る。
 */
public interface HttpFetcher {

    /** 指定 URL を GET し本文を文字列で返す。取得失敗（タイムアウト・接続断・4xx/5xx）は例外を投げる。 */
    String get(String url);
}
