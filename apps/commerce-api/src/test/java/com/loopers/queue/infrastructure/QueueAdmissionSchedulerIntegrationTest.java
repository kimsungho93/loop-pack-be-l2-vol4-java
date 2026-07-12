package com.loopers.queue.infrastructure;

import com.loopers.queue.application.WaitingQueue;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

// 컨텍스트가 캐시에 살아남으면 스케줄러가 다른 테스트의 대기열까지 계속 소비하므로, 클래스 종료 시 컨텍스트를 내린다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
    "commerce.queue.admit-enabled=true",
    "commerce.queue.admit-fixed-delay-ms=50",
})
class QueueAdmissionSchedulerIntegrationTest {

    private final WaitingQueue waitingQueue;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    QueueAdmissionSchedulerIntegrationTest(WaitingQueue waitingQueue, RedisCleanUp redisCleanUp) {
        this.waitingQueue = waitingQueue;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("스케줄러가 켜져 있으면, 줄에 선 사용자가 자동으로 입장 토큰을 발급받는다.")
    @Test
    void issuesTokenAutomatically_whenSchedulerIsEnabled() throws InterruptedException {
        // arrange
        waitingQueue.enter(101L, System.currentTimeMillis());

        // act — fixedDelay 50ms 스케줄러가 배치를 처리할 때까지 잠시 폴링한다.
        boolean admitted = false;
        for (int i = 0; i < 40 && !admitted; i++) {
            Thread.sleep(100);
            admitted = waitingQueue.findToken(101L).isPresent();
        }

        // assert
        assertThat(admitted).isTrue();
    }
}
