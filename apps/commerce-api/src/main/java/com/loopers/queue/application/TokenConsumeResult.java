package com.loopers.queue.application;

public enum TokenConsumeResult {
    /** 토큰을 소비하고 사용됨 마커로 전환 — 주문 진행 가능 */
    CONSUMED,
    /** 이미 소비된 토큰 — 주문이 완료됐을 가능성이 높음 */
    ALREADY_USED,
    /** 토큰 불일치·만료·미발급 — 게이트 통과 불가 */
    INVALID,
    /** 저장소가 응답하지 못해 판정 불가 — 게이트 정책상 통과(fail-open). 뒤의 벌크헤드·DB 가드가 받친다. */
    UNDECIDED,
}
