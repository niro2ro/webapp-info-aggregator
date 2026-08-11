package com.example.aggregator.infra.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LINE 通知の設定（app.line.*）。チャネルアクセストークンは<b>設定ファイルに実値を書かず</b>、環境変数から
 * 注入する（{@code ${LINE_CHANNEL_TOKEN}}・BD-IF-00-01）。無料枠の月上限も設定で外部化する。
 */
@Component
@ConfigurationProperties(prefix = "app.line")
public class LineProperties {

    /** LINE 送信を有効化するか。false（既定）なら NoOp 実装が使われ、トークン無しでも起動する。 */
    private boolean enabled = false;

    /** チャネルアクセストークン（環境変数から注入。実値は application.yml に書かない）。 */
    private String channelToken;

    /** 無料枠の月上限（通）。コミュニケーションプラン=200。この割合に達したら送信を止めアプリ内表示へ。 */
    private int monthlyFreeLimit = 200;

    /** 送信を止める安全マージン（上限のこの割合に達したら送らない・BD-IF-03-03）。 */
    private double limitMargin = 0.95;

    /** 認証不備/ブロックの記事を打ち切る（GaveUp）までの日数（外部IF §3.4）。 */
    private int giveUpAfterDays = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getChannelToken() { return channelToken; }
    public void setChannelToken(String channelToken) { this.channelToken = channelToken; }
    public int getMonthlyFreeLimit() { return monthlyFreeLimit; }
    public void setMonthlyFreeLimit(int monthlyFreeLimit) { this.monthlyFreeLimit = monthlyFreeLimit; }
    public double getLimitMargin() { return limitMargin; }
    public void setLimitMargin(double limitMargin) { this.limitMargin = limitMargin; }
    public int getGiveUpAfterDays() { return giveUpAfterDays; }
    public void setGiveUpAfterDays(int giveUpAfterDays) { this.giveUpAfterDays = giveUpAfterDays; }

    /** 実効の送信上限（通）。当月通数がこれ以上なら送信停止。 */
    public int effectiveLimit() { return (int) (monthlyFreeLimit * limitMargin); }
}
