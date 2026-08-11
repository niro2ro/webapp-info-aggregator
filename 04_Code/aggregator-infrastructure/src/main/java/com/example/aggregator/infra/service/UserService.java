package com.example.aggregator.infra.service;

import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.domain.model.UserRole;
import com.example.aggregator.infra.persistence.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 利用者サービス（DD-CLS-07・FR-07）。自己登録（SC-10）・管理者PIN照合＋ロック（SC-01）・管理者による
 * 利用者管理（SC-09）を担う。
 *
 * <p><b>認証モデル（Phase 5・軽量）</b>: 一般利用者はクレデンシャル無しで選択ログイン、管理者のみ4桁PIN。
 * PINは低エントロピーのため <b>BCrypt でハッシュ保存＋連続失敗ロック</b>で最低限の防御（設計セキュリティ注記）。
 * VPS移行(Phase 6)でパスワード/OAuth 認証へ差し替える（{@code role} は流用）。
 *
 * <p><b>なぜロック状態をメモリに持つか</b>: 数分で解けて永続不要な一時状態のため、DB 変更を避け
 * {@link ConcurrentHashMap} に保持する（localhost 単一プロセス前提。多重化する Phase 6 では DB/Redis へ）。
 */
@Service
public class UserService {

    /** PIN 連続失敗の上限（超過でロック・BD-SC-01-05）。 */
    static final int MAX_PIN_ATTEMPTS = 5;
    /** ロック時間。 */
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ConcurrentHashMap<Long, Attempt> attempts = new ConcurrentHashMap<>();

    // コンストラクタが2つあるため、Spring が生成に使う本番用を @Autowired で明示する
    //（複数コンストラクタで無指定だと Spring は引数なしコンストラクタを探して失敗する）。
    @Autowired
    public UserService(UserRepository users) {
        this(users, new BCryptPasswordEncoder());
    }

    /** テスト用: エンコーダを差し替え可能にする。 */
    UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    // ---- 一覧・参照 ----

    /** ログイン画面に出す有効な利用者一覧（SC-01）。 */
    public List<UserEntity> activeUsers() {
        return users.findByActiveTrueOrderByDisplayName();
    }

    public Optional<UserEntity> find(Long id) {
        return users.findById(id);
    }

    /** 本人のLINE連携設定を更新（SC-05 上段）: 通知先IDと通知の全体ON/OFF。空文字は未登録(null)扱い。 */
    @Transactional
    public void updateLineSettings(Long userId, String lineUserId, boolean notifyEnabled) {
        UserEntity u = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("利用者が見つかりません。"));
        String id = (lineUserId == null || lineUserId.isBlank()) ? null : lineUserId.trim();
        u.setLineUserId(id);
        u.setNotifyEnabled(notifyEnabled);
        users.save(u);
    }

    public List<UserEntity> all() {
        return users.findAll();
    }

    // ---- 自己登録（SC-10・誰でも・role=User 固定） ----

    /** 表示名だけで role=User の利用者を登録する。空・重複はエラー（呼び出し側でメッセージ表示）。 */
    @Transactional
    public UserEntity selfRegister(String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("表示名を入力してください。");
        if (users.existsByDisplayName(name)) throw new IllegalArgumentException("同じ表示名がすでに使われています。");
        return users.save(new UserEntity(name, UserRole.USER));
    }

    // ---- 管理者PIN照合（SC-01・ロック付き） ----

    public enum PinStatus { OK, WRONG, LOCKED }

    /** 照合結果。LOCKED のとき {@code lockRemainingSeconds} に残り秒数を入れる。 */
    public record PinCheck(PinStatus status, long lockRemainingSeconds) {
        public boolean ok() { return status == PinStatus.OK; }
    }

    /**
     * 管理者PINを照合する。PIN 未設定の管理者は初回ブートストラップとして PIN 無しで許可する
     * （seed 直後に管理画面へ入って PIN を設定できるようにするため）。
     */
    public PinCheck verifyAdminPin(Long userId, String pin) {
        Instant now = Instant.now();
        Attempt a = attempts.get(userId);
        if (a != null && a.lockUntil != null && now.isBefore(a.lockUntil)) {
            return new PinCheck(PinStatus.LOCKED, Duration.between(now, a.lockUntil).toSeconds());
        }
        UserEntity user = users.findById(userId).orElse(null);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            return new PinCheck(PinStatus.WRONG, 0);
        }
        // 未設定PIN: ブートストラップ許可（試行カウンタもクリア）。
        if (!user.hasAdminPin()) {
            attempts.remove(userId);
            return new PinCheck(PinStatus.OK, 0);
        }
        if (pin != null && encoder.matches(pin, user.getAdminPinHash())) {
            attempts.remove(userId);
            return new PinCheck(PinStatus.OK, 0);
        }
        // 失敗: カウント。上限到達でロック。
        Attempt next = (a == null) ? new Attempt() : a;
        next.fails++;
        if (next.fails >= MAX_PIN_ATTEMPTS) {
            next.lockUntil = now.plus(LOCK_DURATION);
            next.fails = 0;
            attempts.put(userId, next);
            return new PinCheck(PinStatus.LOCKED, LOCK_DURATION.toSeconds());
        }
        attempts.put(userId, next);
        return new PinCheck(PinStatus.WRONG, 0);
    }

    // ---- 管理者による利用者管理（SC-09・admin のみ） ----

    /** PIN 文字列が4桁数字か。 */
    public static boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }

    @Transactional
    public UserEntity adminCreate(String displayName, UserRole role, String pin, boolean active) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("表示名を入力してください。");
        if (users.existsByDisplayName(name)) throw new IllegalArgumentException("同じ表示名がすでに使われています。");
        UserEntity u = new UserEntity(name, role == null ? UserRole.USER : role);
        u.setActive(active);
        applyPin(u, role, pin);
        return users.save(u);
    }

    @Transactional
    public UserEntity adminUpdate(Long id, String displayName, UserRole role, boolean active, String newPinOrNull) {
        UserEntity u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("利用者が見つかりません。"));
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("表示名を入力してください。");
        u.setDisplayName(name);
        u.setRole(role == null ? UserRole.USER : role);
        u.setActive(active);
        // 一般利用者へ降格したら PIN は無効化。Admin かつ新PIN指定時のみ再設定。
        if (u.getRole() != UserRole.ADMIN) {
            u.setAdminPinHash(null);
        } else if (newPinOrNull != null && !newPinOrNull.isBlank()) {
            applyPin(u, UserRole.ADMIN, newPinOrNull);
        }
        return users.save(u);
    }

    @Transactional
    public void adminDelete(Long id) {
        users.deleteById(id);
        attempts.remove(id);
    }

    /** Admin のときだけ PIN を検証・ハッシュ化して設定。空PINは未設定のまま（ブートストラップ運用）。 */
    private void applyPin(UserEntity u, UserRole role, String pin) {
        if (role == UserRole.ADMIN && pin != null && !pin.isBlank()) {
            if (!isValidPin(pin)) throw new IllegalArgumentException("管理者PINは数字4桁で入力してください。");
            u.setAdminPinHash(encoder.encode(pin));
        }
    }

    /** 連続失敗の一時状態（メモリ保持）。 */
    private static final class Attempt {
        int fails;
        Instant lockUntil;
    }
}
