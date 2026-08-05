---
name: implementation
description: 実装工程の作業をするとき。承認済み詳細設計をもとに .NET ソリューションを Phase 0→5 の順に実装する。C#/.NET固有の書き方は理由を説明し、秘密情報は直書きせず、冪等性・UTC・OS非依存を守る。04_実装/ にソースを置く。
---

# 実装工程

詳細設計に沿って .NET ソリューションを組む工程。**Phase 0 から順に、完成条件を満たしてから次 Phase へ進む**（CLAUDE.md §6）。

## 前提

- `03_詳細設計/` が**レビュー承認済み**であること。
- 設計と実装が食い違ったら、勝手に実装を変えず設計側に戻して整合を取る（設計変更の記録を残す）。

## ソリューション構成（詳細設計の分割に従う）

```
04_実装/
├─ docker-compose.yml        # PostgreSQL をローカル起動
├─ src/
│  ├─ Aggregator.Domain/         # エンティティ・値オブジェクト・ドメインロジック
│  ├─ Aggregator.Infrastructure/ # EF Core, HttpClient, 収集, LINE, Claude API
│  ├─ Aggregator.Web/            # ASP.NET Core + Blazor Server
│  └─ Aggregator.Batch/          # Generic Host コンソール（収集/通知を別エントリ）
└─ tests/ … （単体テストは 05_単体テスト 工程で構築。プロジェクトはここに配置）
```

## Phase 進行（各 Phase の完成条件を満たすまで次に行かない）

- **Phase 0**: `docker compose up -d` で PostgreSQL 起動 → EF Core 接続 → マイグレーションが通る
- **Phase 1**: DB 設計反映、テーマ登録、RSS 収集、一覧表示（1テーマが日付順に並ぶ）
- **Phase 2**: LLM 構造化、重複排除、情報源追加（3ソース以上）
- **Phase 3**: お気に入り、未読管理、フィルタ、検索 → この後に協力者テスト
- **Phase 4**: LINE 通知、冪等性担保（PC 起動時に1通届く）
- **Phase 5**: タスクスケジューラ登録、管理画面、ログ

## 実装ルール（CLAUDE.md §5 を必ず守る）

- **秘密情報を直書きしない**: 接続文字列・Claude APIキー・LINE トークンは User Secrets / 環境変数。コミット対象に含めない（`.gitignore` を整備）。
- **冪等性**: `Articles.UrlHash` ユニーク制約、`ArticleNotifications(UserId,ArticleId)`。DB 制約で担保しアプリチェックだけに頼らない。同日2回目以降は通知しない。
- **UTC 保存 / JST 表示**: 保存は UTC。表示層でのみ JST 変換。`TimeZoneInfo` 識別子を各所に埋め込まず変換ヘルパーに集約。
- **収集と通知を別プロセス**: バッチは Generic Host。通知経路に LLM を呼ばない。
- **OS 非依存**: `Path.Combine` を使い `C:\` を書かない。ローカルファイル依存を避けログは DB / 標準出力へ。Windows サービス化しない。UTF-8 統一。
- **HTTP マナー**: robots.txt 尊重、同一ホスト間隔1秒以上、User-Agent に連絡先。禁止サイトは除外。
- **障害分離**: 1情報源の失敗が全体を止めない（try/catch を情報源単位に、Polly でリトライ）。
- **LLM コスト**: 収集の構造化のみ。RSS 優先。取得は RSS→専用パーサー→LLM のフォールバック。

## C#/.NET の書き方の説明義務

コードを提示するとき、**開発者が未経験の要素は「なぜこう書くか」を添える**（async/await、DI ライフタイム、HttpClientFactory、LINQ、EF Core の追跡挙動、nullable 参照型など）。CLAUDE.md §2 参照。

## 着手時に公式ドキュメントを確認するもの

- LINE Messaging API（push, Flex Message カルーセル, 通数カウント, 料金プラン）
- Claude API（モデルID・リクエスト形式）
- EF Core / Npgsql, AngleSharp, System.ServiceModel.Syndication, Polly のバージョン別の使い方

## 完了条件

各 Phase の完成条件を満たし動作確認できること。実装したユニットは `05_単体テスト`（`unit-testing` skill）でテストする。README に設計判断・権利配慮・コスト設計を記載する（ポートフォリオ評価に直結）。
