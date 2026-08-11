package com.example.aggregator.infra.web;

/**
 * HTTP がエラーステータス（4xx/5xx）を返したことを表す（リトライ非対象の 4xx で使用）。
 * robots ゲートはこの {@code status} を見て「404＝許可扱い」と「その他＝安全側スキップ」を分ける。
 */
public class HttpStatusException extends RuntimeException {
    private final int status;

    public HttpStatusException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
    public boolean isClientError() { return status >= 400 && status < 500; }
}
