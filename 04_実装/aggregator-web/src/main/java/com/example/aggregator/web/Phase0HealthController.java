package com.example.aggregator.web;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 0 の疎通確認用エンドポイント。
 *
 * <p>DB 接続と Flyway マイグレーション適用（テーブル生成）を外形的に確認する。
 * {@code GET /health} で、マイグレーション履歴と主要テーブルの件数を返す。
 * Phase 1 以降で本来の画面（Vaadin）に置き換わる暫定機能。
 */
@RestController
public class Phase0HealthController {

    private final JdbcTemplate jdbc;

    // コンストラクタ注入（詳細設計 DD-DI-01）。DataSource から JdbcTemplate を組み立てる。
    public Phase0HealthController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Integer migrations = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);
        Long users = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        Long sources = jdbc.queryForObject("SELECT count(*) FROM sources", Long.class);
        return Map.of(
                "status", "OK",
                "flywayMigrations", migrations,
                "publicTables", tables,
                "seedUsers", users,
                "seedSources", sources);
    }

    @GetMapping("/health/tables")
    public List<String> tables() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' ORDER BY table_name",
                String.class);
    }
}
