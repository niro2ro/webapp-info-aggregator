package com.example.aggregator.infra.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.example.aggregator.domain.llm.ExtractedText;
import com.example.aggregator.domain.llm.LlmStructurer;
import com.example.aggregator.domain.llm.LlmUsage;
import com.example.aggregator.domain.llm.StructuredArticle;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.domain.model.LlmCallStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Claude API による構造化（DD-CLS-16・外部IF §2）。<b>{@code app.llm.enabled=true} のときだけ</b> Bean 化される
 * （@ConditionalOnProperty）。APIキーは環境変数 {@code ANTHROPIC_API_KEY} から SDK が読む（コード直書き禁止）。
 *
 * <p>呼び出し方針: ①予算ガードで当月上限を確認 → ②JSON 限定で応答させる → ③厳格に parse → ④使用量を記録。
 * 失敗・不正JSON・予算切れは<b>空を返し</b>、収集は RSS 由来の値で継続する（障害分離・NFR-10）。
 * 出力の列挙値は「内部コード値（整数）」で返させ、名称ゆれによる取りこぼしを避ける。
 */
@Component
@ConditionalOnProperty(prefix = "app.llm", name = "enabled", havingValue = "true")
public class ClaudeLlmStructurer implements LlmStructurer {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmStructurer.class);

    /** JSON 以外を出させないための指示（コード値の凡例つき）。本文転載を避け要約のみ求める（§9）。 */
    private static final String SYSTEM_PROMPT = """
            あなたはニュース記事のメタデータ抽出器です。与えられた日本語テキストから次の JSON だけを出力してください。
            前後に説明文やコードブロックを付けず、JSON オブジェクト1個のみを返すこと。
            フィールドと値（列挙は整数コード）:
            {
              "title": string,
              "category": 1=グッズ,2=アニメ,3=漫画,4=イベント,5=ゲーム,6=ゲームセンター,7=カプセルトイ,9=その他,
              "event_date": "YYYY-MM-DD" または null（発売日/開催日など代表日）,
              "event_date_text": string または null（原文の日付表現。例「9月上旬」）,
              "event_date_precision": 0=日まで確定,1=月まで,2=季節,3=継続中,9=不明,
              "event_date_kind": 1=発売,2=開催,3=放送,4=受付開始,9=その他,
              "location": string または null（イベント時の場所）,
              "summary": string（本文を転載せず60字程度の自作要約）
            }
            """;

    private final AnthropicClient client;
    private final LlmProperties props;
    private final LlmBudgetGuard budgetGuard;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public ClaudeLlmStructurer(LlmProperties props, LlmBudgetGuard budgetGuard, ObjectMapper objectMapper) {
        // fromEnv(): 環境変数 ANTHROPIC_API_KEY を読む。enabled=true なのにキー未設定なら起動時に失敗させる
        //（設定ミスは早期に気づく＝fail-fast）。
        this.client = AnthropicOkHttpClient.fromEnv();
        this.props = props;
        this.budgetGuard = budgetGuard;
        this.objectMapper = objectMapper;
        log.info("[LLM] ClaudeLlmStructurer 有効化 model={}", props.getModel());
    }

    /** テスト用: モックのクライアントを注入する。 */
    ClaudeLlmStructurer(AnthropicClient client, LlmProperties props, LlmBudgetGuard budgetGuard, ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.budgetGuard = budgetGuard;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StructuredArticle> structure(ExtractedText input) {
        if (!budgetGuard.hasBudget()) {
            return Optional.empty();  // 予算切れ: 呼ばない（ログは残さない・BD-IF-02-01）
        }

        String userText = "タイトル: " + input.title() + "\nURL: " + input.url() + "\n本文:\n" + input.text();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(props.getModel())
                .maxTokens(props.getMaxTokens())
                .system(SYSTEM_PROMPT)
                .addUserMessage(userText)
                .build();

        Message message;
        try {
            message = client.messages().create(params);
        } catch (RuntimeException e) {
            log.warn("[LLM] 呼び出し失敗 url={} : {}", input.url(), e.toString());
            budgetGuard.recordUsage(LlmUsage.zero(props.getModel()), LlmCallStatus.FAILED, null);
            return Optional.empty();
        }

        LlmUsage usage = new LlmUsage(props.getModel(),
                (int) message.usage().inputTokens(), (int) message.usage().outputTokens());
        String json = firstText(message);
        try {
            StructuredArticle article = parse(json);
            budgetGuard.recordUsage(usage, LlmCallStatus.SUCCESS, null);
            return Optional.of(article);
        } catch (RuntimeException e) {
            // JSON 不正: この記事はスキップ（RSS の値で登録）。使用量は課金済みなので記録する。
            log.warn("[LLM] 応答JSONの解釈に失敗 url={} : {}", input.url(), e.toString());
            budgetGuard.recordUsage(usage, LlmCallStatus.FORMAT_ERROR, null);
            return Optional.empty();
        }
    }

    /** 応答から最初のテキストブロックを取り出す。 */
    private static String firstText(Message message) {
        for (ContentBlock block : message.content()) {
            if (block.isText()) return block.asText().text();
        }
        return "";
    }

    private StructuredArticle parse(String json) {
        try {
            LlmJson j = objectMapper.readValue(json.trim(), LlmJson.class);
            return new StructuredArticle(
                    j.title(),
                    j.category() == null ? null : Category.fromCode(j.category().shortValue()),
                    j.event_date() == null || j.event_date().isBlank() ? null : LocalDate.parse(j.event_date()),
                    j.event_date_text(),
                    j.event_date_precision() == null ? EventDatePrecision.UNKNOWN
                            : EventDatePrecision.fromCode(j.event_date_precision().shortValue()),
                    j.event_date_kind() == null ? null : EventDateKind.fromCode(j.event_date_kind().shortValue()),
                    j.location(),
                    j.summary());
        } catch (Exception e) {
            throw new IllegalStateException("LLM JSON parse error", e);
        }
    }

    /** 応答 JSON の受け皿（不明フィールドは無視）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmJson(String title, Integer category, String event_date, String event_date_text,
                   Integer event_date_precision, Integer event_date_kind, String location, String summary) {}
}
