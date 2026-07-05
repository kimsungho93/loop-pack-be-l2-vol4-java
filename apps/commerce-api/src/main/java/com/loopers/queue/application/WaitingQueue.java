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

    boolean consumeToken(long userId, String token);
}
