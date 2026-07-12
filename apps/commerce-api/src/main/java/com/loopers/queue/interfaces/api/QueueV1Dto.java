package com.loopers.queue.interfaces.api;

import com.loopers.queue.application.QueueEnterInfo;
import com.loopers.queue.application.QueuePositionInfo;
import com.loopers.queue.domain.QueueEntryStatus;

public class QueueV1Dto {

    public record EnterResponse(
        /* 순번 조회(X-Waiting-Token 헤더)에 사용하는 가벼운 식별 토큰. 주문에 쓰는 입장 토큰과 다르다. */
        String waitingToken,
        QueueEntryStatus status,
        Long position,
        Long totalWaiting,
        Long estimatedWaitSeconds,
        Long pollAfterSeconds
    ) {

        public static EnterResponse from(QueueEnterInfo info) {
            return new EnterResponse(
                info.waitingToken(),
                info.position().status(),
                info.position().position(),
                info.position().totalWaiting(),
                info.position().estimatedWaitSeconds(),
                info.position().pollAfterSeconds()
            );
        }
    }

    public record PositionResponse(
        QueueEntryStatus status,
        Long position,
        Long totalWaiting,
        /* 순번 ÷ 초당 처리량으로 계산한 추정값이다. 실제 대기 시간과 다를 수 있다. */
        Long estimatedWaitSeconds,
        /* 다음 순번 조회까지 기다릴 시간. WAITING 일 때만 값이 있고, 나머지 상태는 폴링을 중단한다. */
        Long pollAfterSeconds,
        String token
    ) {

        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(
                info.status(),
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.pollAfterSeconds(),
                info.token()
            );
        }
    }
}
