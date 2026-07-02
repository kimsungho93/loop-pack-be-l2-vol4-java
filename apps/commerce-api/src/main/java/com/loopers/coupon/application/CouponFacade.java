package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestService;
import com.loopers.coupon.domain.CouponService;
import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final CouponIssueRequestService couponIssueRequestService;
    private final CouponIssueGate couponIssueGate;
    private final CouponIssueRequestPublisher couponIssueRequestPublisher;
    private final UserCouponListQuery userCouponListQuery;

    public IssuedCouponInfo issueCoupon(IssueCouponCommand command) {
        try {
            return IssuedCouponInfo.from(couponService.issueCoupon(command.userId(), command.couponTemplateId()));
        } catch (CoreException e) {
            return findIssuedCoupon(command).orElseThrow(() -> e);
        }
    }

    public CouponIssueRequestInfo requestIssue(IssueCouponCommand command) {
        CouponTemplate couponTemplate = couponService.getCouponTemplate(command.couponTemplateId());
        if (!couponTemplate.hasQuantityLimit()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량 한정 쿠폰이 아닙니다. 즉시 발급 API를 사용해주세요.");
        }

        checkGate(command.userId(), couponTemplate);

        CouponIssueRequest request = couponIssueRequestService.accept(command.userId(), couponTemplate.getId());
        publish(request);
        return CouponIssueRequestInfo.from(request);
    }

    public CouponIssueRequestInfo getIssueRequest(Long userId, Long requestId) {
        CouponIssueRequest request = couponIssueRequestService.getRequest(requestId);
        if (!request.isRequestedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 발급 요청입니다.");
        }
        return CouponIssueRequestInfo.from(request);
    }

    private void checkGate(Long userId, CouponTemplate couponTemplate) {
        CouponIssueGateResult gateResult = couponIssueGate.tryPass(
            userId,
            couponTemplate.getId(),
            couponTemplate.getTotalQuantity(),
            couponTemplate.getExpiration().expiredAt()
        );
        if (gateResult == CouponIssueGateResult.DUPLICATE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다.");
        }
        if (gateResult == CouponIssueGateResult.SOLD_OUT) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다.");
        }
    }

    private void publish(CouponIssueRequest request) {
        try {
            couponIssueRequestPublisher.publish(CouponIssueRequestMessage.from(request));
        } catch (RuntimeException e) {
            couponIssueRequestService.fail(request.getId(), "발급 요청 이벤트 발행에 실패했습니다.");
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        return userCouponListQuery.findMyCoupons(userId, ZonedDateTime.now());
    }

    private Optional<IssuedCouponInfo> findIssuedCoupon(IssueCouponCommand command) {
        return couponService.findIssuedCoupon(command.userId(), command.couponTemplateId())
            .map(IssuedCouponInfo::from);
    }
}
