package com.example.aggregator.domain.model;

/** 利用者ロール（users.role・smallint コード値）。1:User（一般）/ 9:Admin（管理者）。 */
public enum UserRole {
    USER(1), ADMIN(9);

    private final short code;
    UserRole(int code) { this.code = (short) code; }
    public short code() { return code; }
    public static UserRole fromCode(short code) {
        for (UserRole v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の UserRole コード: " + code);
    }
}
