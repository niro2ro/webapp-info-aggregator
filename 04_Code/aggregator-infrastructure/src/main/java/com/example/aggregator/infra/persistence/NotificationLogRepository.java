package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 通知送信ログ。当月（JST 月）通数の合計で無料枠（200通）を管理する（FR-03-05・BD-IF-03-03）。 */
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, Long> {

    /**
     * 当月（Asia/Tokyo の暦月）の消費通数合計。sent_at は timestamptz(UTC)。JST に変換して月初と比較する。
     * 成功送信のみが通数を消費するため status=0(Success) に限定する。
     */
    @Query(value = """
            SELECT COALESCE(SUM(message_count), 0)
            FROM notification_logs
            WHERE status = 0
              AND (sent_at AT TIME ZONE 'Asia/Tokyo')
                  >= date_trunc('month', (now() AT TIME ZONE 'Asia/Tokyo'))
            """, nativeQuery = true)
    int sumCurrentMonthMessageCount();
}
