package com.example.aggregator.web;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web アプリのエントリポイント（詳細設計 DD-ARC-07）。
 *
 * <p>{@code @Theme("aggregator")} で Lumo ベースのカスタムテーマ（和ノスタルジック＋七宝の地紋・
 * BD-SC-00-09/§0.1）を適用する。{@code AppShellConfigurator} を実装したクラスに付けるのが Vaadin の作法。
 */
@SpringBootApplication(scanBasePackages = "com.example.aggregator")
@Theme("aggregator")
public class WebApplication implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
