package com.example.aggregator.infra.service;

import com.example.aggregator.infra.notify.LineProperties;
import com.example.aggregator.infra.persistence.NotificationLogRepository;
import org.springframework.stereotype.Component;

/**
 * 当月通数の集計と無料枠判定（DD-CLS-10・FR-03-05）。上限接近で送信を止めるための判断を1箇所に集約する。
 * 通数 ＝ 送信先ユーザー数 × 吹き出し数。カルーセル集約で吹き出しは1に抑える（BD-IF-03-01）。
 */
@Component
public class NotificationCountService {

    private final NotificationLogRepository logs;
    private final LineProperties props;

    public NotificationCountService(NotificationLogRepository logs, LineProperties props) {
        this.logs = logs;
        this.props = props;
    }

    /** 当月（JST 月）に消費済みの通数。 */
    public int currentMonthCount() { return logs.sumCurrentMonthMessageCount(); }

    /** まだ送ってよいか（実効上限＝無料枠×マージン未満）。 */
    public boolean canSend() { return currentMonthCount() < props.effectiveLimit(); }

    /** 残り送信可能通数（表示用・SC-07）。 */
    public int remaining() { return Math.max(0, props.effectiveLimit() - currentMonthCount()); }
}
