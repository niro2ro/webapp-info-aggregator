package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.BookmarkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** ブックマーク（後で見る）のリポジトリ。ブックマーク済み記事の取得は ArticleRepository 側に置く。 */
public interface BookmarkRepository extends JpaRepository<BookmarkEntity, BookmarkEntity.Key> {

    boolean existsByKey(BookmarkEntity.Key key);

    List<BookmarkEntity> findByKeyUserId(Long userId);
}
