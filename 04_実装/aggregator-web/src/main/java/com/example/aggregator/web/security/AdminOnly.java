package com.example.aggregator.web.security;

/**
 * 管理者専用画面のマーカー（SC-06〜09）。このインターフェースを実装したビューは、
 * {@link AuthGuard} が admin 以外の到達を拒否する（メニュー非表示だけに頼らない・BD-SC-00-06）。
 */
public interface AdminOnly {
}
