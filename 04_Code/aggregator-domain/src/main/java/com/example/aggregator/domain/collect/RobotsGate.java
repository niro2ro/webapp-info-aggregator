package com.example.aggregator.domain.collect;

/**
 * robots.txt ゲートのポート（DD-CLS-15・FR-02-05）。取得直前に対象URLが許可されているか判定する。
 * 実装は infrastructure（crawler-commons＋日次キャッシュ）。
 *
 * <p>安全側の方針（BD-BATCH-C-03）: robots.txt が「無い/404」＝許可扱い。ただし robots 自体の
 * 取得エラー（タイムアウト等）は<b>安全側で当日スキップ（不許可扱い）</b>にする。
 */
public interface RobotsGate {

    /** 指定URLの取得が robots.txt 上許可されているか。取得エラー時は false（当日スキップ）。 */
    boolean isAllowed(String url);
}
