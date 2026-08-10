package com.example.aggregator.domain.model;

/**
 * 情報の種別（カテゴリ）。8区分（要件 D2・テーブル定義書 §0 の Category）。
 *
 * <p>DB には列挙順ではなく<b>コード値（smallint）</b>で保存する。理由: Hibernate 既定の
 * {@code @Enumerated(ORDINAL)} は列挙の宣言順に暗黙依存し、並べ替え／挿入で既存データが壊れる。
 * ここでは {@link #code()} を明示し、{@code AttributeConverter} で相互変換する
 * （詳細設計 DD-DAO-09）。将来カテゴリを増やす場合はコードを追加するだけでスキーマ変更は不要。
 */
public enum Category {
    GOODS(1),
    ANIME(2),
    MANGA(3),
    EVENT(4),
    GAME(5),
    ARCADE(6),
    CAPSULE_TOY(7),
    OTHER(9);

    private final short code;

    Category(int code) {
        this.code = (short) code;
    }

    /** DB 保存用のコード値。 */
    public short code() {
        return code;
    }

    /** コード値から enum へ復元する（未知コードは例外）。 */
    public static Category fromCode(short code) {
        for (Category c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        throw new IllegalArgumentException("未知の Category コード: " + code);
    }
}
