package com.example.aggregator.infra.service;

import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.persistence.SourceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 情報源マスタのサービス（DD-CLS-08・SC-06）。<b>規約ゲート</b>の要: {@code terms_reviewed=true} かつ
 * {@code is_active=true} の情報源だけが収集対象になる（FR-02-12）。削除は原則不可（ON DELETE RESTRICT）で、
 * 停止は {@code active=false}（無効化）で行う。
 */
@Service
public class SourceService {

    private final SourceRepository sources;

    public SourceService(SourceRepository sources) {
        this.sources = sources;
    }

    public List<SourceEntity> all() {
        return sources.findAll();
    }

    @Transactional
    public SourceEntity create(String name, String url, FetchType fetchType,
                               boolean active, boolean termsReviewed, String termsNote, boolean robotsRespect) {
        validate(name, url);
        SourceEntity s = new SourceEntity(name.trim(), url.trim(), fetchType == null ? FetchType.RSS : fetchType);
        s.setActive(active);
        s.setTermsReviewed(termsReviewed);   // true なら確認日を自動記録
        s.setTermsNote(termsNote);
        s.setRobotsRespect(robotsRespect);
        return sources.save(s);
    }

    @Transactional
    public SourceEntity update(Long id, String name, String url, FetchType fetchType,
                               boolean active, boolean termsReviewed, String termsNote, boolean robotsRespect) {
        SourceEntity s = sources.findById(id).orElseThrow(() -> new IllegalArgumentException("情報源が見つかりません。"));
        validate(name, url);
        s.setName(name.trim());
        s.setUrl(url.trim());
        s.setFetchType(fetchType == null ? FetchType.RSS : fetchType);
        s.setActive(active);
        // 確認状態が変わったときだけ setTermsReviewed を呼び、確認日を更新/クリアする。
        if (s.isTermsReviewed() != termsReviewed) {
            s.setTermsReviewed(termsReviewed);
        }
        s.setTermsNote(termsNote);
        s.setRobotsRespect(robotsRespect);
        return sources.save(s);
    }

    private void validate(String name, String url) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("名称を入力してください。");
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URLを入力してください。");
    }
}
