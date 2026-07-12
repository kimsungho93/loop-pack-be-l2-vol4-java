package com.loopers.coupon.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestService {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponIssueRequest accept(Long userId, Long couponTemplateId) {
        return couponIssueRequestRepository.save(
            CouponIssueRequest.request(userId, couponTemplateId, ZonedDateTime.now())
        );
    }

    @Transactional
    public void fail(Long requestId, String reason) {
        couponIssueRequestRepository.findById(requestId)
            .ifPresent(request -> request.markFailed(reason, ZonedDateTime.now()));
    }

    @Transactional(readOnly = true)
    public CouponIssueRequest getRequest(Long requestId) {
        return couponIssueRequestRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다."));
    }

    @Transactional
    public void process(Long requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다."));
        if (request.isProcessed()) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        Optional<CouponTemplate> couponTemplate = couponTemplateRepository.findActiveById(request.getCouponTemplateId());
        if (couponTemplate.isEmpty()) {
            request.markFailed("존재하지 않는 쿠폰입니다.", now);
            return;
        }

        Optional<UserCoupon> issuedCoupon =
            userCouponRepository.findIssuedCoupon(request.getUserId(), request.getCouponTemplateId());
        if (issuedCoupon.isPresent()) {
            request.markAlreadyIssued(issuedCoupon.get().getId(), now);
            return;
        }

        issueWithinQuantity(request, couponTemplate.get(), now);
    }

    private void issueWithinQuantity(CouponIssueRequest request, CouponTemplate couponTemplate, ZonedDateTime now) {
        UserCoupon newCoupon;
        try {
            newCoupon = couponTemplate.issue(request.getUserId(), now);
        } catch (CoreException e) {
            request.markFailed(e.getMessage(), now);
            return;
        }

        if (!couponTemplateRepository.increaseIssuedCount(request.getCouponTemplateId())) {
            request.markSoldOut(now);
            return;
        }

        // 선조회 직후 끼어든 동시 중복 발급의 유니크 위반은 잡지 않는다 —
        // 트랜잭션이 이미 rollback-only라 상태 전이를 커밋할 수 없으므로, 전파해 재전달에 맡긴다.
        UserCoupon saved = userCouponRepository.save(newCoupon);
        request.markIssued(saved.getId(), now);
    }
}
