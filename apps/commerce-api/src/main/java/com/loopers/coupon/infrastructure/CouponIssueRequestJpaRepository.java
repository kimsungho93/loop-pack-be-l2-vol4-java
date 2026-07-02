package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {
}
