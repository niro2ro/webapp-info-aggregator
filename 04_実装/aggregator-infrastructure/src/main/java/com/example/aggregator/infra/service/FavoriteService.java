package com.example.aggregator.infra.service;

import com.example.aggregator.domain.model.FavoriteSourceEntity;
import com.example.aggregator.domain.model.FavoriteThemeEntity;
import com.example.aggregator.infra.persistence.FavoriteSourceRepository;
import com.example.aggregator.infra.persistence.FavoriteThemeRepository;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * お気に入り（テーマ／情報源）の登録・解除・通知ON/OFF（FR-05-01/02/04）。
 * 「お気に入り＝通知する／ブックマーク＝後で見るだけ（通知しない）」を分けて扱う（用語定義 §1）。
 */
@Service
public class FavoriteService {

    private final FavoriteThemeRepository favThemes;
    private final FavoriteSourceRepository favSources;

    public FavoriteService(FavoriteThemeRepository favThemes, FavoriteSourceRepository favSources) {
        this.favThemes = favThemes;
        this.favSources = favSources;
    }

    // themeId -> notifyEnabled（お気に入り登録中のみ含む）
    public Map<Long, Boolean> themeFavorites(Long userId) {
        return favThemes.findByKeyUserId(userId).stream()
                .collect(Collectors.toMap(f -> f.getKey().getThemeId(), FavoriteThemeEntity::isNotifyEnabled));
    }

    public Map<Long, Boolean> sourceFavorites(Long userId) {
        return favSources.findByKeyUserId(userId).stream()
                .collect(Collectors.toMap(f -> f.getKey().getSourceId(), FavoriteSourceEntity::isNotifyEnabled));
    }

    /** テーマお気に入りをトグル。登録時の通知既定は ON。戻り値: 登録中か。 */
    @Transactional
    public boolean toggleThemeFavorite(Long userId, Long themeId) {
        FavoriteThemeEntity.Key key = new FavoriteThemeEntity.Key(userId, themeId);
        if (favThemes.existsById(key)) {
            favThemes.deleteById(key);
            return false;
        }
        favThemes.save(new FavoriteThemeEntity(userId, themeId, true));
        return true;
    }

    @Transactional
    public void setThemeNotify(Long userId, Long themeId, boolean notify) {
        FavoriteThemeEntity.Key key = new FavoriteThemeEntity.Key(userId, themeId);
        favThemes.findById(key).ifPresent(f -> { f.setNotifyEnabled(notify); favThemes.save(f); });
    }

    @Transactional
    public boolean toggleSourceFavorite(Long userId, Long sourceId) {
        FavoriteSourceEntity.Key key = new FavoriteSourceEntity.Key(userId, sourceId);
        if (favSources.existsById(key)) {
            favSources.deleteById(key);
            return false;
        }
        favSources.save(new FavoriteSourceEntity(userId, sourceId, true));
        return true;
    }

    @Transactional
    public void setSourceNotify(Long userId, Long sourceId, boolean notify) {
        FavoriteSourceEntity.Key key = new FavoriteSourceEntity.Key(userId, sourceId);
        favSources.findById(key).ifPresent(f -> { f.setNotifyEnabled(notify); favSources.save(f); });
    }
}
