package com.loopers.queue.interfaces.api;

import com.loopers.queue.application.QueuePositionInfo;
import com.loopers.queue.domain.QueueEntryStatus;

public class QueueV1Dto {

    public record PositionResponse(
        QueueEntryStatus status,
        Long position,
        Long totalWaiting,
        /* 순번 ÷ 초당 처리량으로 계산한 추정값이다. 실제 대기 시간과 다를 수 있다. */
        Long estimatedWaitSeconds,
        String token
    ) {

        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(
                info.status(),
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.token()
            );
        }
    }
}
