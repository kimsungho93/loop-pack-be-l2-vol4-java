package com.loopers.queue.application;

public enum TokenConsumeResult {
    /** 토큰을 소비하고 사용됨 마커로 전환 — 주문 진행 가능 */
    CONSUMED,
    /** 이미 소비된 토큰 — 주문이 완료됐을 가능성이 높음 */
    ALREADY_USED,
    /** 토큰 불일치·만료·미발급 — 게이트 통과 불가 */
    INVALID,
}
