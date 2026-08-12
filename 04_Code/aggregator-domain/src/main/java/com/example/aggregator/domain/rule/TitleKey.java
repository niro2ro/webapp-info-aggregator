package com.example.aggregator.domain.rule;

/**
 * 重複判定用のタイトルキー（同一タイトルの記事を1件に集約する・FR-02-09 の簡易版）。
 *
 * <p>正規化の方針:
 * <ul>
 *   <li>末尾の「 - 媒体名」を除去（Googleニュースの検索結果は「記事タイトル - Yahoo!ニュース」等の形式のため、
 *       別媒体からの同一記事を同じキーにする）</li>
 *   <li>連続する空白（全角space含む）を1つに畳み、前後を trim</li>
 * </ul>
 * ※副作用として「新商品 - 予約開始」のように本文に " - " を含む短いタイトルは末尾が削られることがあるが、
 * その場合は同じ前半どうしが集約される（重複排除を優先）。
 */
public final class TitleKey {

    private TitleKey() {}

    public static String of(String title) {
        if (title == null) return "";
        // 末尾の「 - 媒体名」（ハイフンを含まない最後の1区切り）を除去
        String stripped = title.replaceAll(" - [^-]*$", "");
        // 空白（半角/全角/タブ等）を1つに畳んで trim
        return stripped.replaceAll("[\\s　]+", " ").trim();
    }
}
