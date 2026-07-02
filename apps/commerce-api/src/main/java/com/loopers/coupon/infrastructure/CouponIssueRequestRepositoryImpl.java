package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        return couponIssueRequestJpaRepository.save(request);
    }

    @Override
    public Optional<CouponIssueRequest> findById(Long requestId) {
        return couponIssueRequestJpaRepository.findById(requestId);
    }
}
