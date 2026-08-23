package com.example.aggregator.infra.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LINE「合言葉」連携（ユーザーが userId を入力しない登録フロー）。
 *
 * <p><b>流れ</b>: ①画面で利用者が「合言葉を発行」→ 一時コード（6桁）を発行して保持。②利用者は公式アカウントを
 * <b>友だち追加</b>し、そのコードをトークで送る。③LINE から Webhook でコード本文＋送信者の {@code userId} が届く
 * → コードに紐づく利用者に {@code line_user_id} を保存する。これで利用者は自分の userId を知る/入力する必要がない。
 *
 * <p>コードは短命（既定10分）でメモリ保持（localhost 単一プロセス前提。永続不要な一時状態のため DB を汚さない・
 * PIN ロックと同方針）。多重化する Phase 6 では DB/Redis へ移す。実際の紐付け（DB更新）は {@link UserService} に委譲。
 */
@Service
public class LineLinkService {

    private static final Logger log = LoggerFactory.getLogger(LineLinkService.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService users;
    /** code → (userId, expiresAt)。合言葉の未使用・未期限のものだけ有効。 */
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

    public LineLinkService(UserService users) {
        this.users = users;
    }

    /** 利用者向けに新しい合言葉を発行する（既存の未使用コードは破棄して1人1つに保つ）。 */
    public String issueCode(Long userId) {
        pending.values().removeIf(p -> p.userId.equals(userId));   // 同一利用者の古いコードを掃除
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        pending.put(code, new Pending(userId, Instant.now().plus(TTL)));
        log.info("[LINE連携] 合言葉を発行 user={}（有効{}分）", userId, TTL.toMinutes());
        return code;
    }

    /**
     * Webhook がトーク本文と送信者 userId を渡してくる。本文が有効な合言葉なら、その利用者に line_user_id を
     * 保存して連携完了。合言葉でなければ何もしない（true=連携した / false=対象外）。
     */
    public boolean linkFromMessage(String lineUserId, String messageText) {
        if (lineUserId == null || messageText == null) return false;
        String code = messageText.trim();
        Pending p = pending.get(code);
        if (p == null) return false;
        if (Instant.now().isAfter(p.expiresAt)) {   // 期限切れは掃除して対象外
            pending.remove(code);
            return false;
        }
        users.linkLine(p.userId, lineUserId);   // DB更新（line_user_id 保存）は UserService に委譲
        pending.remove(code);
        log.info("[LINE連携] 合言葉一致 → 連携完了 user={}", p.userId);
        return true;
    }

    /** 画面の「連携状況を更新」用: いま有効な合言葉が残っているか（発行済み・未使用・未期限）。 */
    public Optional<String> activeCodeFor(Long userId) {
        Instant now = Instant.now();
        return pending.entrySet().stream()
                .filter(e -> e.getValue().userId.equals(userId) && now.isBefore(e.getValue().expiresAt))
                .map(java.util.Map.Entry::getKey)
                .findFirst();
    }

    private record Pending(Long userId, Instant expiresAt) {}
}
