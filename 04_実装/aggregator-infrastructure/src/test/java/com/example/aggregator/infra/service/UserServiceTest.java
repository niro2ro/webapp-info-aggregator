package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.model.UserEntity;
import com.example.aggregator.domain.model.UserRole;
import com.example.aggregator.infra.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 認証まわり（自己登録・PIN照合・ロック）の要点を固定する。BCrypt は本物を使う（照合の正しさも検証）。 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, new BCryptPasswordEncoder());
        lenient().when(users.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---- 自己登録（SC-10） ----

    @Test
    @DisplayName("自己登録は role=User で保存される")
    void selfRegisterCreatesUser() {
        when(users.existsByDisplayName("ひろP")).thenReturn(false);
        UserEntity u = service.selfRegister("  ひろP  ");   // 前後空白はトリム
        assertThat(u.getDisplayName()).isEqualTo("ひろP");
        assertThat(u.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("空名・重複名はエラー")
    void selfRegisterValidates() {
        assertThatThrownBy(() -> service.selfRegister("  ")).isInstanceOf(IllegalArgumentException.class);
        when(users.existsByDisplayName("既存")).thenReturn(true);
        assertThatThrownBy(() -> service.selfRegister("既存")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 管理者PIN（SC-01） ----

    private UserEntity admin(Long id, String pinPlain) {
        UserEntity a = new UserEntity("管理者", UserRole.ADMIN);
        if (pinPlain != null) a.setAdminPinHash(new BCryptPasswordEncoder().encode(pinPlain));
        when(users.findById(id)).thenReturn(java.util.Optional.of(a));
        return a;
    }

    @Test
    @DisplayName("正しいPINはOK、違うPINはWRONG")
    void verifyPin() {
        admin(1L, "1234");
        assertThat(service.verifyAdminPin(1L, "1234").ok()).isTrue();
        assertThat(service.verifyAdminPin(1L, "0000").status()).isEqualTo(UserService.PinStatus.WRONG);
    }

    @Test
    @DisplayName("PIN未設定の管理者はブートストラップとしてOK（PIN無しで許可）")
    void bootstrapNoPin() {
        admin(2L, null);
        assertThat(service.verifyAdminPin(2L, "").ok()).isTrue();
    }

    @Test
    @DisplayName("5回連続失敗でロックされる")
    void locksAfterFiveFailures() {
        admin(3L, "1234");
        for (int i = 0; i < UserService.MAX_PIN_ATTEMPTS - 1; i++) {
            assertThat(service.verifyAdminPin(3L, "9999").status()).isEqualTo(UserService.PinStatus.WRONG);
        }
        UserService.PinCheck last = service.verifyAdminPin(3L, "9999");   // 5回目
        assertThat(last.status()).isEqualTo(UserService.PinStatus.LOCKED);
        assertThat(last.lockRemainingSeconds()).isGreaterThan(0);
        // ロック中は正しいPINでもLOCKED
        assertThat(service.verifyAdminPin(3L, "1234").status()).isEqualTo(UserService.PinStatus.LOCKED);
    }

    // ---- 管理者CRUD（SC-09） ----

    @Test
    @DisplayName("Admin作成時、PINはハッシュ化され平文で残らない")
    void adminCreateHashesPin() {
        when(users.existsByDisplayName(any())).thenReturn(false);
        UserEntity u = service.adminCreate("新管理者", UserRole.ADMIN, "4321", true);
        assertThat(u.getAdminPinHash()).isNotNull().isNotEqualTo("4321");
        assertThat(new BCryptPasswordEncoder().matches("4321", u.getAdminPinHash())).isTrue();
    }

    @Test
    @DisplayName("PINが4桁数字でなければエラー")
    void adminCreateRejectsBadPin() {
        when(users.existsByDisplayName(any())).thenReturn(false);
        assertThatThrownBy(() -> service.adminCreate("x", UserRole.ADMIN, "12", true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
