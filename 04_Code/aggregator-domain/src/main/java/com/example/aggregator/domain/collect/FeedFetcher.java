package com.example.aggregator.domain.collect;

import java.util.List;

/**
 * フィード取得のポート（domain）。実装（ROME + HTTP）は infrastructure が提供し DI で注入する
 * （依存性逆転・DD-CLS-12）。テストではスタブに差し替えられる。
 */
public interface FeedFetcher {

    /** 指定フィードURLを取得・解析して生データ一覧を返す。取得失敗は例外を投げる（呼び出し側で分離）。 */
    List<RawItem> fetch(String feedUrl);
}
