package com.example.aggregator.domain.rule;

import com.example.aggregator.domain.model.EventDatePrecision;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 発売日（実イベント日）の<b>ルールベース抽出</b>（LLMを使わない・無料・即時・オフライン）。
 * 記事のタイトル・要約に含まれる日本語の日付表現を正規表現で解釈し、代表日＋精度＋原文を返す。
 *
 * <p>設計方針（C案の下段）: 収集時にまずこのルールで発売日を埋め、埋まらなかった曖昧なものだけを
 * 「発売日順」選択時などにオンデマンドで LLM 補完する。これで<b>発売日順は基本 LLM 無しで即時</b>になる。
 * ※取得手段は RSS のまま。ここでやるのは「取得済みテキストから日付文字列を解釈する」ことだけ
 * （HTMLスクレイピング＝廃止した専用パーサーとは別物）。
 *
 * <p>対応表現（代表日 / 精度）:
 * <ul>
 *   <li>{@code 2026年9月18日} / {@code 2026/9/18} … 正確日（EXACT）</li>
 *   <li>{@code 9月18日}（年なし） … 正確日。年は参照日から推定（発表は通常これからなので過去すぎれば翌年）</li>
 *   <li>{@code 9月上旬/中旬/下旬} … 月内おおよそ（MONTH・代表日=5/15/25）</li>
 *   <li>{@code 2026年9月} / {@code 9月}（発売等の語が近い場合） … 月まで（MONTH・代表日=1日）</li>
 *   <li>{@code 2026年春/夏/秋/冬} … 季節（SEASON・代表月=3/6/9/12の1日）</li>
 * </ul>
 * 曖昧すぎる相対表現（今冬・来春 等）は対象外（LLM フォールバックに委ねる）。
 */
public class EventDateExtractor {

    /** 抽出結果。{@code date}=代表日（並び替え用）、{@code precision}=確からしさ、{@code text}=原文の該当箇所。 */
    public record Extracted(LocalDate date, EventDatePrecision precision, String text) {}

    // 年つき: 2026年9月18日 / 2026/9/18 / 2026-9-18
    private static final Pattern YMD_KANJI = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern YMD_SLASH = Pattern.compile("(\\d{4})\\s*[/./-]\\s*(\\d{1,2})\\s*[/./-]\\s*(\\d{1,2})");
    // 年なし: 9月18日
    private static final Pattern MD_KANJI = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    // 上旬/中旬/下旬（年は任意）
    private static final Pattern JUN = Pattern.compile("(?:(\\d{4})\\s*年\\s*)?(\\d{1,2})\\s*月\\s*(上旬|中旬|下旬)");
    // 年つき月まで: 2026年9月
    private static final Pattern YM = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月(?!\\s*\\d)");
    // 季節（年つき）: 2026年春
    private static final Pattern SEASON_P = Pattern.compile("(\\d{4})\\s*年\\s*(春|夏|秋|冬)");
    // 月のみ（年なし・発売等の語が近くにある場合だけ採用）: 9月
    private static final Pattern MONTH_ONLY = Pattern.compile("(\\d{1,2})\\s*月(?!\\s*\\d)");

    /** 発表は通常「これから」。参照日よりこの日数以上過去なら翌年とみなす。 */
    private static final int PAST_SLACK_DAYS = 60;

    /**
     * テキストから代表日を1つ抽出する。見つからなければ empty（呼び出し側は LLM 補完や NULL 継続へ）。
     * より具体的な表現から順に試し、最初に妥当な日付が取れたものを返す。
     *
     * @param text      タイトル＋要約など
     * @param reference 年の推定に使う参照時刻（記事の掲載日など。null なら現在）
     */
    public Optional<Extracted> extract(String text, Instant reference) {
        if (text == null || text.isBlank()) return Optional.empty();
        LocalDate ref = (reference == null ? Instant.now() : reference).atZone(TimeZones.JST).toLocalDate();

        // ① 年つき正確日
        Optional<Extracted> r = ymd(YMD_KANJI, text);
        if (r.isPresent()) return r;
        r = ymd(YMD_SLASH, text);
        if (r.isPresent()) return r;

        // ② 上旬/中旬/下旬（MONTH 精度・代表日を旬の中央付近に）
        Matcher m = JUN.matcher(text);
        if (m.find()) {
            Integer year = m.group(1) != null ? Integer.parseInt(m.group(1)) : null;
            int month = Integer.parseInt(m.group(2));
            int day = switch (m.group(3)) { case "上旬" -> 5; case "中旬" -> 15; default -> 25; };
            Optional<LocalDate> d = build(year, month, day, ref);
            if (d.isPresent()) return Optional.of(new Extracted(d.get(), EventDatePrecision.MONTH, m.group()));
        }

        // ③ 年なし正確日: 9月18日
        m = MD_KANJI.matcher(text);
        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            Optional<LocalDate> d = build(null, month, day, ref);
            if (d.isPresent()) return Optional.of(new Extracted(d.get(), EventDatePrecision.EXACT, m.group()));
        }

        // ④ 年つき月まで: 2026年9月（代表日=1日・MONTH）
        m = YM.matcher(text);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            Optional<LocalDate> d = build(year, month, 1, ref);
            if (d.isPresent()) return Optional.of(new Extracted(d.get(), EventDatePrecision.MONTH, m.group()));
        }

        // ⑤ 季節（年つき）: 2026年春（代表月=3/6/9/12・SEASON）
        m = SEASON_P.matcher(text);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int month = switch (m.group(2)) { case "春" -> 3; case "夏" -> 6; case "秋" -> 9; default -> 12; };
            Optional<LocalDate> d = build(year, month, 1, ref);
            if (d.isPresent()) return Optional.of(new Extracted(d.get(), EventDatePrecision.SEASON, m.group()));
        }

        // ⑥ 月のみ（年なし）: 発売/放送/開催/公開/登場 等の語が本文に含まれるときだけ採用（誤検出を抑える）
        if (hasReleaseCue(text)) {
            m = MONTH_ONLY.matcher(text);
            if (m.find()) {
                int month = Integer.parseInt(m.group(1));
                Optional<LocalDate> d = build(null, month, 1, ref);
                if (d.isPresent()) return Optional.of(new Extracted(d.get(), EventDatePrecision.MONTH, m.group()));
            }
        }
        return Optional.empty();
    }

    private Optional<Extracted> ymd(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return Optional.empty();
        try {
            LocalDate d = LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            return Optional.of(new Extracted(d, EventDatePrecision.EXACT, m.group()));
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();   // 2026年13月40日 のような不正値は不採用
        }
    }

    /** 年ありならその年、年なしなら参照日から推定して日付を組む。不正な日付は empty。 */
    private Optional<LocalDate> build(Integer year, int month, int day, LocalDate ref) {
        if (month < 1 || month > 12 || day < 1 || day > 31) return Optional.empty();
        int y = (year != null) ? year : ref.getYear();
        try {
            LocalDate d = LocalDate.of(y, month, day);
            if (year == null && d.isBefore(ref.minusDays(PAST_SLACK_DAYS))) {
                d = LocalDate.of(y + 1, month, day);   // 過去すぎる年なし日付は翌年（＝これからの発表とみなす）
            }
            return Optional.of(d);
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private boolean hasReleaseCue(String t) {
        return t.contains("発売") || t.contains("放送") || t.contains("開催")
                || t.contains("公開") || t.contains("登場") || t.contains("配信") || t.contains("予約");
    }
}
