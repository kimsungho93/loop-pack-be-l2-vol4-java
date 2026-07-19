package com.loopers.metrics.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class ProductMetricHourlyId {

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
