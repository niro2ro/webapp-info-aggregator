package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.aggregator.infra.persistence.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * DI 配線の回帰テスト。{@link UserService} はコンストラクタが2つ（本番用・テスト用）あるため、
 * Spring に「どちらで生成するか」を @Autowired で示していないと "No default constructor found" で
 * 起動失敗する（実機で発生）。本番と同じ経路（Spring のコンストラクタ解決）で生成できることを固定する。
 */
class UserServiceWiringTest {

    @Test
    @DisplayName("UserService が Spring のコンストラクタ解決で生成できる（引数なしコンストラクタ不要）")
    void instantiableBySpring() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(UserRepository.class, () -> mock(UserRepository.class));
            ctx.registerBean(UserService.class);   // @Autowired コンストラクタが選ばれるはず
            ctx.refresh();
            assertThat(ctx.getBean(UserService.class)).isNotNull();
        }
    }
}
