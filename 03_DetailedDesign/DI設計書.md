# DI設計書 — 情報収集ツール（詳細設計）

| 項目 | 内容 |
|---|---|
| 版 | 1.0（詳細設計・初版） |
| 作成日 | 2026-08-08 |
| 前工程 | [`クラス設計書.md`](クラス設計書.md)／[`アーキテクチャ設計書.md`](アーキテクチャ設計書.md) |
| 設計ID体系 | `DD-DI-nn` |

> Spring の DI（依存性注入）の方針を確定する。**DI は「`new` で具象を掴まず、必要な相手をコンテナに入れてもらう」仕組み**。Delphi では自分で生成/管理していた依存を、Spring コンテナが生成・注入する。ここでは Java 実務未経験者向けに**なぜその方針か**を明記する（CLAUDE.md §2）。

---

## 1. 注入方法：コンストラクタ注入を基本とする（DD-DI-01）

```java
@Service
public class CollectionService {
    private final FeedFetcher feedFetcher;
    private final ArticleRepository articleRepository;
    private final LlmBudgetGuard budgetGuard;

    // コンストラクタが1つなら @Autowired は不要（Spring が自動で注入）
    public CollectionService(FeedFetcher feedFetcher,
                             ArticleRepository articleRepository,
                             LlmBudgetGuard budgetGuard) {
        this.feedFetcher = feedFetcher;
        this.articleRepository = articleRepository;
        this.budgetGuard = budgetGuard;
    }
}
```

**なぜコンストラクタ注入か（フィールド注入 `@Autowired` を使わない理由）**:

| 理由 | 説明 |
|---|---|
| 不変にできる | 依存を `final` にでき、生成後に差し替わらない（スレッド安全・予測可能） |
| 必須依存が明確 | コンストラクタ引数＝必須依存。足りなければ起動時に落ちる（実行時 NPE より早い） |
| テスト容易 | テストで `new CollectionService(モック, モック, モック)` と**Spring 無しでも生成**できる（単体テスト工程の前提） |
| 循環依存を検出 | コンストラクタ注入だと循環依存が起動時に露見し設計の是正を促す |

> フィールド注入（`@Autowired private X x;`）は上記が崩れるため使わない。setter 注入は「任意依存」の限定用途のみ。

---

## 2. Bean スコープの方針（DD-DI-02）

| スコープ | 使う対象 | 理由 |
|---|---|---|
| **singleton（既定）** | サービス・リポジトリ・ポート実装・ルール（`CollectionService`/`*Repository`/`RomeFeedFetcher`/`UrlNormalizer` 等） | **状態を持たない**（フィールドは注入された依存のみ）ので1インスタンスを共有して安全・省メモリ。処理ごとの状態は引数/戻り値/ローカル変数で扱う |
| prototype | 原則使わない | 状態を持つ部品が必要でも、まず「状態はメソッド引数へ」を検討。どうしても必要なら都度 `new`（Bean にしない） |
| Vaadin セッション/UIスコープ | 画面（View）クラス | Vaadin がUI（ブラウザセッション）単位で管理。**画面は表示状態のみ持ち、業務状態は持たない**（DD-CLS-11） |

> **なぜサービスが singleton で安全か（DD-DI-03）**: サービスは「注入された依存（すべて不変）」しか持たず、リクエストごとのデータは**メソッドの引数とローカル変数**で流す。共有可変状態がないので複数スレッド（仮想スレッド収集・Vaadin の複数UI）から同時に呼ばれても競合しない。

---

## 3. インフラ Bean の一元定義（DD-DI-04）

`RestClient` や外部SDKクライアントは `@Configuration` で**1回だけ生成**して Bean 化する。

```java
@Configuration
public class HttpClientConfig {
    @Bean
    RestClient contentRestClient(RestClient.Builder builder,
                                 @Value("${app.user-agent}") String ua) {
        return builder
            .defaultHeader("User-Agent", ua)   // アプリ名＋連絡先（BD-IF-01-03）
            .build();
    }
}
```

**なぜ `RestClient` を都度 `new` せず Bean 化するか（DD-DI-05）**:
- コネクションプール・タイムアウト・共通ヘッダ（User-Agent）・Resilience4j 設定を**一箇所に集約**でき、毎回作る無駄（コネクション再確立）を避ける。
- 収集の全取得で同じ設定を使わせられる（マナー遵守を仕組みで担保）。

同様に `ClaudeLlmStructurer`・`LineBotNotifier`・`ObjectMapper`（JSON）も Bean 化。APIキー/トークンは `@Value("${...}")` で**環境変数から**注入（コードに直書きしない・[`設定・秘密情報設計書.md`](設定・秘密情報設計書.md)）。

---

## 4. プロファイルとバッチ別アプリでの Bean（DD-DI-06）

- 3つの起動クラス（Web/収集/通知）は同じ domain/infrastructure を共有するが、**必要な Bean だけをコンポーネントスキャン**する。通知アプリでは `LlmStructurer` を配線しない（**通知経路に LLM を置かない**＝CLAUDE.md §5 を配線レベルで保証）。
- `@Profile("web"|"collection"|"notification")` や起動クラスのスキャン範囲で、プロセスごとの Bean 集合を分ける。
- 秘密情報の有無で挙動を変えない（未設定は起動時エラーで気付く・DD-CFG）。

---

## 5. トレーサビリティ

| 基本設計/要件 | 本書 |
|---|---|
| BD-IF-00-03（差し替え・ポート） | DD-DI-01/03 |
| CLAUDE.md §5（通知にLLM不使用） | DD-DI-06 |
| テスト容易性（テスト工程の前提） | DD-DI-01 |
| BD-IF-01-03（UA連絡先）・BD-IF-00-01（秘密情報） | DD-DI-04/05 |
