# テーマ別最新情報アグリゲーター — 実装（04_Code）

アニメ・漫画などのテーマを登録すると、複数の情報源から最新情報（グッズ／イベント／放送／カプセルトイ等）を自動収集し、発生日順に一覧できる Web アプリ。お気に入り対象の新着は LINE で通知する。**Java（Spring Boot + Vaadin Flow）学習を兼ねた転職用ポートフォリオ**。

> 上位ドキュメント: 要件定義 `01_要件定義/`・基本設計 `02_基本設計/`・詳細設計 `03_詳細設計/`。本フォルダはそれらに沿った実装。

## 技術構成

Java 21 / Spring Boot 3.3 / Maven マルチモジュール / PostgreSQL 16 / Flyway / Spring Data JPA + Hibernate。UI は Vaadin Flow（Phase 1〜）、収集/通知は別プロセスのバッチ（`CommandLineRunner`）。

```
04_Code/
├─ docker-compose.yml          # ローカル PostgreSQL
├─ pom.xml                     # 親POM（マルチモジュール）
├─ aggregator-domain/          # エンティティ・enum・ドメインルール（フレームワーク非依存）
├─ aggregator-infrastructure/  # JPA・Flyway・外部API/収集アダプタ（db/migration に V1/V2）
├─ aggregator-web/             # Web アプリ（Phase0: /health、Phase1: Vaadin）
└─ aggregator-batch/           # 収集/通知バッチ（別実行可能jar）
```

## セットアップ（ローカル）

```bash
# 1) 秘密情報テンプレをコピーして値を設定（.env はコミットされない）
cp .env.example .env       # 必要に応じて編集

# 2) PostgreSQL を起動
docker compose up -d

# 3) ビルド（日本語パスのため UTF-8 ロケール必須）
LANG=C.UTF-8 LC_ALL=C.UTF-8 mvn -DskipTests install

# 4) Web アプリ起動（起動時に Flyway が V1/V2 を適用）
LANG=C.UTF-8 LC_ALL=C.UTF-8 mvn -pl aggregator-web spring-boot:run
#   もしくは: java -jar aggregator-web/target/aggregator-web-0.1.0-SNAPSHOT.jar
```

## Phase 進行（CLAUDE.md §6）

| Phase | 内容 | 状態 |
|---|---|---|
| **0** | Docker で PostgreSQL 起動 → Spring Boot + JPA 接続 → **Flyway マイグレーションが通る** | ✅ 完了（下記） |
| **1** | テーマ登録・RSS 収集・一覧表示 | 🟡 収集/冪等/テーマ突合/日付順の**中核を実装・検証**（デモAPI）。正式画面(Vaadin)は次段 |
| 2 | LLM 構造化・重複排除・情報源追加 | 未 |
| 3 | お気に入り・未読・フィルタ・検索 | 未 |
| 4 | LINE 通知・冪等性 | 未 |
| 5 | スケジューラ・管理画面・ログ | 未 |

### Phase 0 完成条件と確認方法

`docker compose up -d` → Web 起動後に:

```bash
curl -s http://localhost:8080/health
# {"status":"OK","flywayMigrations":2,"publicTables":15,"seedUsers":2,"seedSources":4}
curl -s http://localhost:8080/health/tables   # 14テーブル + flyway_schema_history
```

- Flyway が **V1（全14テーブル＋制約＋索引）** と **V2（シード：利用者2・情報源4）** を適用。
- 実装で確定した点: タイムライン整列の式インデックスは `timestamptz::date` が **IMMUTABLE でない**ため、`(created_at AT TIME ZONE 'UTC')::date` を用いる（テーブル定義書 §2.1 に反映済み）。

### Phase 1 の確認方法（デモAPI・ネットワーク不要）

JPAエンティティ／リポジトリ（DB設計反映）と収集パイプライン（RSS解析→URL正規化→ハッシュ→冪等→種別判定→テーマ突合→日付順一覧）を、バンドルしたサンプルRSSで外形確認する。正式なUIは Vaadin（次段）。

```bash
curl -s -X POST localhost:8080/demo/seed-theme   # テーマ「呪術廻戦」登録
curl -s -X POST localhost:8080/demo/collect      # {"total":4,"registered":4,"duplicated":0}
curl -s -X POST localhost:8080/demo/collect      # 2回目 {"registered":0,"duplicated":4} ← 冪等
curl -s localhost:8080/demo/timeline             # 発生日順（降順）
curl -s localhost:8080/demo/timeline/theme       # テーマにマッチした記事のみ
```

実装した中核（詳細設計対応）: `ArticleEntity`ほかエンティティ＋`AttributeConverter`（enumコード値・DD-DAO-09）、Spring Data リポジトリ、`UrlNormalizer`/`UrlHasher`/`EventDateKindResolver`（domain.rule）、`RomeFeedFetcher`（ROME・FR-02-01）、`CollectionService`（冪等・突合・DD-SEQ-01）。式インデックスに一致するネイティブSQLで一覧応答（NFR-04）。

## 設計上の遵守事項（ポートフォリオの要点・CLAUDE.md §5）

- **権利配慮**: 記事本文・公式画像を保存/表示しない（**自作要約＋元URLのみ**）。robots.txt を自動ゲートで尊重、利用規約は人手確認ゲート（`terms_reviewed`）で担保。同一ホスト間隔1秒以上、User-Agent に連絡先。禁止サイトは除外。
- **AIコスト**: LLM は収集時の構造化のみ。RSS→専用パーサー→LLM のフォールバックで LLM 使用を最小化。当月コストを `usage`×単価で自前計算し **月500円をハードキャップ**（上限到達で LLM 停止・RSS で継続）。通知処理では LLM を呼ばない（配線でも保証）。
- **冪等性**: `articles.url_hash` UNIQUE、`article_notifications(user_id, article_id)` 複合PK。DB 制約を最終防衛線とし、同日2回目以降は通知しない。
- **UTC 保存 / JST 表示**: DB は `timestamptz`、Java は `Instant`（UTC）。JST 変換は表示層に集約（`ZoneId.of("Asia/Tokyo")` を一箇所に）。
- **移行性（VPS）**: OS 非依存（`Path.of`）、設定は環境変数で外部化、ログは DB／標準出力、SQLite 不使用、UTF-8。
- **秘密情報**: DBパスワード／Claude APIキー／LINE トークンは環境変数（`.env` はローカルのみ・`.gitignore` 済）。`application.yml` はプレースホルダのみ。

## 注意

- 本リポジトリ環境（クラウド）では Docker Hub からのイメージ取得が制限される場合がある。その場合はローカル PostgreSQL でも可（`initdb`＋`pg_ctl`）。開発は自宅 PC で `docker compose` を推奨。
- ライブラリのバージョンや外部 API 仕様（LINE / Claude）は改定されうるため、各 Phase 着手時に公式ドキュメントで確認する。
