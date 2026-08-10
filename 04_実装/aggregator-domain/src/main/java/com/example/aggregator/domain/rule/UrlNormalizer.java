package com.example.aggregator.domain.rule;

import java.net.URI;

/**
 * URL 正規化（FR-02-07）。同一記事を同じキーに落とすため、表記ゆれを吸収する。
 *
 * <p>方針: スキーム/ホストを小文字化、フラグメント(#...)除去、クエリ(?...)除去、
 * 既定ポート除去、末尾スラッシュ除去。解析に失敗したら trim した元文字列を返す（頑健性優先）。
 * リダイレクト解決は取得層で行い、その結果 URL を本メソッドに渡す想定。
 */
public class UrlNormalizer {

    public String normalize(String rawUrl) {
        if (rawUrl == null) return null;
        String s = rawUrl.trim();
        try {
            URI u = URI.create(s);
            String scheme = u.getScheme() == null ? "https" : u.getScheme().toLowerCase();
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            int port = u.getPort();
            boolean defaultPort = (port == -1)
                    || ("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443);
            String path = u.getPath() == null ? "" : u.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (!defaultPort) sb.append(':').append(port);
            sb.append(path);
            // クエリ・フラグメントは意図的に落とす
            return sb.toString();
        } catch (RuntimeException e) {
            return s;
        }
    }
}
