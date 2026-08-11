package com.example.aggregator.domain.llm;

/**
 * LLM 構造化の入力（DD-CLS-26）。本文抽出テキストとページの title/url をまとめた不変 DTO。
 *
 * <p>{@code record} を使う理由: 値だけを運ぶ DTO は不変が望ましく、equals/hashCode/toString が
 * 自動生成されるため定型コードを書かずに済む（Delphi の record 型に近いが不変）。
 */
public record ExtractedText(String title, String url, String text) {}
