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
}
