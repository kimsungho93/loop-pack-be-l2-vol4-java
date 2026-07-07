package com.loopers.queue.application;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final WaitingQueue waitingQueue;
    private final QueueProperties queueProperties;

    public QueueEnterInfo enter(long userId) {
        try {
            QueueEnterResult result = waitingQueue.enter(userId, System.currentTimeMillis());
            String waitingToken = UUID.randomUUID().toString();
            waitingQueue.saveWaitingToken(waitingToken, userId, queueProperties.waitingTokenTtl());
            QueuePositionInfo position = QueuePositionInfo.waiting(
                result.position(), result.totalWaiting(), queueProperties.permitsPerSecond(), queueProperties.poll());
            return new QueueEnterInfo(waitingToken, position);
        } catch (DataAccessException e) {
            throw storeUnavailable(e);
        }
    }

    public QueuePositionInfo getPositionByWaitingToken(String waitingToken) {
        try {
            return waitingQueue.findUserIdByWaitingToken(waitingToken)
                .map(this::getPosition)
                .orElseGet(QueuePositionInfo::expired);
        } catch (DataAccessException e) {
            throw storeUnavailable(e);
        }
    }

    // 대기열 저장소 장애는 서버 오류(500)가 아니라 재시도 안내(503)로 응답해,
    // 클라이언트가 돌격 대신 백오프 재시도를 하도록 유도한다. 주문 게이트는 반대로 fail-open.
    private CoreException storeUnavailable(DataAccessException e) {
        log.error("Waiting queue store unavailable.", e);
        return new CoreException(ErrorType.SERVICE_UNAVAILABLE, "대기열 상태를 잠시 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    public QueuePositionInfo getPosition(long userId) {
        return waitingQueue.findRank(userId)
            .map(rank -> QueuePositionInfo.waiting(rank + 1, waitingQueue.countWaiting(), queueProperties.permitsPerSecond(), queueProperties.poll()))
            .orElseGet(() -> waitingQueue.findToken(userId)
                .map(QueuePositionInfo::ready)
                .orElseGet(() -> waitingQueue.isTokenUsed(userId)
                    ? QueuePositionInfo.completed()
                    : QueuePositionInfo.expired()));
    }
}
