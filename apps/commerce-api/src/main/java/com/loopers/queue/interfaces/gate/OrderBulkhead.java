package com.loopers.queue.interfaces.gate;

import com.loopers.queue.application.QueueProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class OrderBulkhead {

    private final Semaphore permits;

    public OrderBulkhead(QueueProperties queueProperties) {
        this.permits = new Semaphore(queueProperties.orderConcurrencyLimit());
    }

    public boolean tryEnter() {
        return permits.tryAcquire();
    }

    public void exit() {
        permits.release();
    }
}
