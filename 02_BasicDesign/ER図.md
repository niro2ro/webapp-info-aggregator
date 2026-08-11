# ER図 — テーマ別最新情報アグリゲーター（基本設計）

| 項目 | 内容 |
|---|---|
| 版 | 1.0（基本設計・初版） |
| 作成日 | 2026-08-08 |
| 前工程 | [`01_Requirements/要件定義書.md`](../01_Requirements/要件定義書.md) §6 データ設計（承認済み v1.5） |
| 設計ID体系 | 本書のエンティティ関連＝`BD-ER-xx`。テーブルは要件の `TBL-xxx` を継承 |

> 本書は要件定義 §6（DDLレベル）で確定した14テーブルの**エンティティ関連**を図示する。カラム・型・制約・インデックス・保持期間は [`テーブル定義書.md`](テーブル定義書.md) に定義する（役割分担）。列挙値（Category / NotifyStatus 等）も同書 §列挙値を参照。

---

## 1. 全体 ER 図（Mermaid `erDiagram`）

エンティティ間の関連と多重度（1対多）を示す。PK/FK は主要列のみ抜粋（全カラムは [`テーブル定義書.md`](テーブル定義書.md)）。

```mermaid
erDiagram
    USERS ||--o{ THEMES : "所有"
    USERS ||--o{ FAVORITE_THEMES : "登録"
    USERS ||--o{ FAVORITE_SOURCES : "登録"
    USERS ||--o{ BOOKMARKS : "保存"
    USERS ||--o{ READ_STATES : "既読管理"
    USERS ||--o{ ARTICLE_NOTIFICATIONS : "通知履歴"
    USERS ||--o{ NOTIFICATION_LOGS : "送信先"

    THEMES ||--o{ THEME_CATEGORIES : "収集対象カテゴリ"
    THEMES ||--o{ ARTICLE_THEME_MATCHES : "突合"
    THEMES ||--o{ FAVORITE_THEMES : "お気に入り対象"

    SOURCES ||--o{ ARTICLES : "取得元"
    SOURCES ||--o{ FAVORITE_SOURCES : "お気に入り対象"
    SOURCES ||--o{ CRAWL_LOGS : "収集ログ"

    ARTICLES ||--o{ ARTICLE_THEME_MATCHES : "マッチ"
    ARTICLES ||--o{ BOOKMARKS : "ブックマーク"
    ARTICLES ||--o{ READ_STATES : "既読対象"
    ARTICLES ||--o{ ARTICLE_NOTIFICATIONS : "通知対象"

    CRAWL_LOGS ||--o{ LLM_USAGE_LOGS : "紐づく収集実行(任意)"

    USERS {
        bigint id PK
        text display_name
        smallint role "UserRole"
        text admin_pin_hash "NULL可"
        text line_user_id "UNIQUE,NULL可"
        boolean notify_enabled
        timestamptz last_notified_at "NULL可"
    }
    THEMES {
        bigint id PK
        bigint user_id FK
        text keyword
        boolean is_active
    }
    THEME_CATEGORIES {
        bigint theme_id PK_FK
        smallint category PK "Category"
    }
    SOURCES {
        bigint id PK
        text name
        text url
        smallint fetch_type "FetchType"
        boolean is_active
        boolean terms_reviewed "規約ゲート"
        boolean robots_respect
    }
    ARTICLES {
        bigint id PK
        bigint source_id FK
        text title
        smallint category "Category"
        date event_date "NULL可"
        text event_date_text "NULL可"
        smallint event_date_precision "EventDatePrecision"
        smallint event_date_kind "EventDateKind"
        text url
        text url_hash "UNIQUE"
        text summary "NULL可"
        text group_key "NULL可"
        timestamptz created_at
    }
    ARTICLE_THEME_MATCHES {
        bigint article_id PK_FK
        bigint theme_id PK_FK
        timestamptz matched_at
    }
    FAVORITE_THEMES {
        bigint user_id PK_FK
        bigint theme_id PK_FK
        boolean notify_enabled
    }
    FAVORITE_SOURCES {
        bigint user_id PK_FK
        bigint source_id PK_FK
        boolean notify_enabled
    }
    BOOKMARKS {
        bigint user_id PK_FK
        bigint article_id PK_FK
        timestamptz created_at
    }
    READ_STATES {
        bigint user_id PK_FK
        bigint article_id PK_FK
        timestamptz read_at
    }
    ARTICLE_NOTIFICATIONS {
        bigint user_id PK_FK
        bigint article_id PK_FK
        timestamptz notified_at
        smallint result "0:Delivered/1:GaveUp"
    }
    NOTIFICATION_LOGS {
        bigint id PK
        bigint user_id FK
        timestamptz sent_at
        int article_count
        int message_count
        smallint status "NotifyStatus"
    }
    CRAWL_LOGS {
        bigint id PK
        bigint source_id FK
        timestamptz started_at
        int item_count
        int new_item_count
        smallint status "CrawlStatus"
    }
    LLM_USAGE_LOGS {
        bigint id PK
        bigint crawl_log_id FK "NULL可"
        timestamptz called_at
        varchar model
        int input_tokens
        int output_tokens
        bigint est_cost_micro_jpy
        smallint status "LlmCallStatus"
    }
```

> Mermaid 記法メモ: `||--o{` は「1対多（左が1・右が多、右は0件可）」。`PK_FK` は複合主キーであり同時に外部キーである列（中間テーブルに多い）。図の可読性のため一部カラムを省略している。

---

## 2. リレーション一覧（多重度・ON DELETE）

要件 §6「参照アクション（ON DELETE）方針」を関連ごとに再掲し、基本設計の関連IDを付す。ON DELETE は [`テーブル定義書.md`](テーブル定義書.md) の各テーブル定義と一致させる。

| 関連ID | 親 | 子 | 多重度 | ON DELETE | 根拠（要件） |
|---|---|---|---|---|---|
| BD-ER-01 | Users | Themes | 1 : N | CASCADE | 利用者削除時に個人データ一括除去（VPS後の退会想定） |
| BD-ER-02 | Users | FavoriteThemes | 1 : N | CASCADE | 同上 |
| BD-ER-03 | Users | FavoriteSources | 1 : N | CASCADE | 同上 |
| BD-ER-04 | Users | Bookmarks | 1 : N | CASCADE | 同上 |
| BD-ER-05 | Users | ReadStates | 1 : N | CASCADE | 同上 |
| BD-ER-06 | Users | ArticleNotifications | 1 : N | CASCADE | 同上 |
| BD-ER-07 | Users | NotificationLogs | 1 : N | CASCADE | 送信先利用者の履歴。利用者削除時に連動 |
| BD-ER-08 | Themes | ThemeCategories | 1 : N | CASCADE | テーマ削除でカテゴリ指定を整理 |
| BD-ER-09 | Themes | ArticleThemeMatches | 1 : N | CASCADE | テーマ削除でマッチ切り離し（FR-01-03 を DB で担保） |
| BD-ER-10 | Themes | FavoriteThemes | 1 : N | CASCADE | テーマ削除でお気に入りを整理 |
| BD-ER-11 | Sources | Articles | 1 : N | **RESTRICT** | 収集済み記事のある情報源の誤削除を防止（無効化は `is_active=false`） |
| BD-ER-12 | Sources | FavoriteSources | 1 : N | **RESTRICT** | 同上（情報源は論理無効化で運用） |
| BD-ER-13 | Sources | CrawlLogs | 1 : N | **RESTRICT** | 同上 |
| BD-ER-14 | Articles | ArticleThemeMatches | 1 : N | CASCADE | 記事パージ（NFR-05）時に付随データを孤児化させない |
| BD-ER-15 | Articles | Bookmarks | 1 : N | CASCADE | ※ブックマーク済み記事はパージ対象外のため通常は連動しない |
| BD-ER-16 | Articles | ReadStates | 1 : N | CASCADE | 記事パージ時に連動 |
| BD-ER-17 | Articles | ArticleNotifications | 1 : N | CASCADE | 記事パージ時に連動 |
| BD-ER-18 | CrawlLogs | LlmUsageLogs | 1 : N（任意） | **SET NULL** | CrawlLogs はログ1ヶ月でパージ（NFR-05）。`crawl_log_id` はトレース用の nullable なので親削除時は NULL 化し LlmUsageLogs（当月コスト集計）は残す |

> ON DELETE の3方針が意味を持つ場面:
> - **CASCADE**（個人データ・記事付随）＝親が消えるべきときに一緒に消す。孤児レコードを作らない。
> - **RESTRICT**（Sources）＝親（情報源）を消させない。収集履歴のある情報源はうっかり削除できず、無効化（`is_active=false`）で運用する。DB が「消せない」と拒否することで運用ミスを防ぐ。
> - **SET NULL**（CrawlLogs→LlmUsageLogs）＝親（収集ログ）は1ヶ月でパージされるが、子（LLMコスト実績）は月次集計に必要なので消さず、リンクだけ NULL にする。

---

## 3. エンティティの役割分類

14テーブルを役割で3分類する。基本設計・詳細設計での関心の分離に使う。

| 分類 | テーブル | 役割 |
|---|---|---|
| マスタ／利用者データ | Users, Themes, ThemeCategories, Sources | 利用者が登録・管理する基準データ |
| 収集データ（本体） | Articles, ArticleThemeMatches | 収集フェーズが生成する記事とテーマ突合 |
| 利用者×記事の関連 | FavoriteThemes, FavoriteSources, Bookmarks, ReadStates, ArticleNotifications | 「誰がどの対象／記事をどう扱うか」の交差テーブル。多くが複合PKで冪等性を担保 |
| ログ／実績 | NotificationLogs, CrawlLogs, LlmUsageLogs | バッチ実行の記録。通数・コスト集計と監査の根拠 |

---

## 4. 冪等性が現れる箇所（DB制約レベル）

要件 NFR-07 の冪等性が、ER 上どの制約で担保されるかを明示する（詳細は [`テーブル定義書.md`](テーブル定義書.md) の制約欄）。

| 冪等性の対象 | 担保する制約 | エンティティ |
|---|---|---|
| 記事の重複登録防止 | `url_hash` の UNIQUE | Articles |
| 記事×テーマの重複突合防止 | 複合PK `(article_id, theme_id)` | ArticleThemeMatches |
| 重複通知防止（再送しない） | 複合PK `(user_id, article_id)` | ArticleNotifications |
| お気に入りの重複登録防止 | 複合PK `(user_id, theme_id)` / `(user_id, source_id)` | FavoriteThemes / FavoriteSources |
| 既読の重複防止 | 複合PK `(user_id, article_id)` | ReadStates |

---

## 5. トレーサビリティ

- 全14テーブルが要件 §6 の `TBL-xxx` と1対1で対応（過不足なし）。テーブル→要件の対応は [`要件トレース表.md`](要件トレース表.md) で一覧化する。
- 本 ER 図は「関連（構造）」のみを担い、属性の物理定義は [`テーブル定義書.md`](テーブル定義書.md)、画面での見え方は [`画面設計書.md`](画面設計書.md) が担う。
