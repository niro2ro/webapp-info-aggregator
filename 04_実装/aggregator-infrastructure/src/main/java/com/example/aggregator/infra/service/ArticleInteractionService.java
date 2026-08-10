package com.example.aggregator.infra.service;

import com.example.aggregator.domain.model.BookmarkEntity;
import com.example.aggregator.domain.model.ReadStateEntity;
import com.example.aggregator.infra.persistence.BookmarkRepository;
import com.example.aggregator.infra.persistence.ReadStateRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 記事に対する利用者操作（既読・ブックマーク）。行の有無で状態を表す冪等な設計（FR-04-04・FR-05-03）。
 */
@Service
public class ArticleInteractionService {

    private final ReadStateRepository readStates;
    private final BookmarkRepository bookmarks;

    public ArticleInteractionService(ReadStateRepository readStates, BookmarkRepository bookmarks) {
        this.readStates = readStates;
        this.bookmarks = bookmarks;
    }

    @Transactional
    public void markRead(Long userId, Long articleId) {
        ReadStateEntity.Key key = new ReadStateEntity.Key(userId, articleId);
        if (!readStates.existsByKey(key)) {
            readStates.save(new ReadStateEntity(userId, articleId));
        }
    }

    /** ブックマークをトグルし、操作後の状態（true=登録中）を返す。 */
    @Transactional
    public boolean toggleBookmark(Long userId, Long articleId) {
        BookmarkEntity.Key key = new BookmarkEntity.Key(userId, articleId);
        if (bookmarks.existsByKey(key)) {
            bookmarks.deleteById(key);
            return false;
        }
        bookmarks.save(new BookmarkEntity(userId, articleId));
        return true;
    }

    public Set<Long> readArticleIds(Long userId) {
        return readStates.findByKeyUserId(userId).stream()
                .map(r -> r.getKey().getArticleId()).collect(Collectors.toSet());
    }

    public Set<Long> bookmarkedArticleIds(Long userId) {
        return bookmarks.findByKeyUserId(userId).stream()
                .map(b -> b.getKey().getArticleId()).collect(Collectors.toSet());
    }
}
