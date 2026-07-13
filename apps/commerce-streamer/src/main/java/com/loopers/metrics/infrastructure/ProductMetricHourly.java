package com.loopers.metrics.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_metric_hourly")
public class ProductMetricHourly {

    @EmbeddedId
    private ProductMetricHourlyId id;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_delta", nullable = false)
    private long likeDelta;

    @Column(name = "order_quantity", nullable = false)
    private long orderQuantity;

    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
