package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ReadStateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 既読状態のリポジトリ（複合PK）。行の有無で既読/未読を表す。 */
public interface ReadStateRepository extends JpaRepository<ReadStateEntity, ReadStateEntity.Key> {

    boolean existsByKey(ReadStateEntity.Key key);

    List<ReadStateEntity> findByKeyUserId(Long userId);
}
