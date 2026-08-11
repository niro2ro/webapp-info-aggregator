package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.UserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 利用者リポジトリ。通知バッチは「通知可能な利用者」を起点に処理する。 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /** 通知対象の利用者＝通知ON かつ LINE 連携済み（line_user_id あり）。 */
    List<UserEntity> findByNotifyEnabledTrueAndLineUserIdNotNull();
}
