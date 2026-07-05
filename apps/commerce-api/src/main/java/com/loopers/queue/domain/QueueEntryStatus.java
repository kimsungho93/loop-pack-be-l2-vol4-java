package com.loopers.queue.domain;

public enum QueueEntryStatus {
    /** 줄에 서서 차례를 기다리는 중 */
    WAITING,
    /** 입장 토큰이 발급되어 주문 가능 */
    READY,
    /** 입장 토큰으로 주문을 완료함 */
    COMPLETED,
    /** 줄에도 없고 유효한 토큰도 없음 — 재진입 필요 */
    EXPIRED,
}
