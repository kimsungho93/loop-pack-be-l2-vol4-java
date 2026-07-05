package com.loopers.queue.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface WaitingQueue {

    QueueEnterResult enter(long userId, long enterAtMillis);

    Optional<Long> findRank(long userId);

    long countWaiting();

    /**
     * 대기열 앞에서부터 최대 {@code tokens.size()}명을 꺼내 입장 토큰을 발급한다.
     *
     * @return 실제로 입장시킨 인원 수
     */
    int admit(List<String> tokens, Duration tokenTtl);

    Optional<String> findToken(long userId);

    /**
     * 토큰 값이 일치하면 소비하고 사용됨 마커(TTL {@code usedMarkerTtl})로 바꾼다.
     * 마커가 남아 있는 동안의 재소비는 {@link TokenConsumeResult#ALREADY_USED} 로 구분된다.
     */
    TokenConsumeResult consumeToken(long userId, String token, Duration usedMarkerTtl);

    /** 토큰이 소비되어 사용됨 마커 상태인지 확인한다. */
    boolean isTokenUsed(long userId);

    /**
     * 사용됨 마커 상태의 토큰을 원래 값으로 되돌린다 (주문 실패 시 차례 보존).
     * 마커가 이미 만료됐으면 부활시키지 않는다.
     *
     * @return 실제로 복구했으면 true
     */
    boolean restoreToken(long userId, String token, Duration tokenTtl);
}
