package com.loopers.coupon.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.coupon.domain.vo.CouponOwner;
import com.loopers.coupon.domain.vo.CouponTemplateId;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "coupon_issue_request",
    indexes = @Index(name = "idx_coupon_issue_request_user", columnList = "user_id, coupon_template_id")
)
public class CouponIssueRequest extends BaseEntity {

    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "user_id", nullable = false))
    private CouponOwner owner;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "coupon_template_id", nullable = false))
    private CouponTemplateId couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueRequestStatus status;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    private String reason;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private CouponIssueRequest(Long userId, Long couponTemplateId, ZonedDateTime requestedAt) {
        this.owner = CouponOwner.of(userId);
        this.couponTemplateId = CouponTemplateId.of(couponTemplateId);
        this.status = CouponIssueRequestStatus.REQUESTED;
        this.requestedAt = requireRequestedAt(requestedAt);
    }

    public static CouponIssueRequest request(Long userId, Long couponTemplateId, ZonedDateTime requestedAt) {
        return new CouponIssueRequest(userId, couponTemplateId, requestedAt);
    }

    public Long getUserId() {
        return owner.userId();
    }

    public Long getCouponTemplateId() {
        return couponTemplateId.value();
    }

    public boolean isProcessed() {
        return status.isProcessed();
    }

    public boolean isRequestedBy(Long userId) {
        return owner.isSameUser(userId);
    }

    public void markIssued(Long userCouponId, ZonedDateTime processedAt) {
        checkNotProcessed();
        this.status = CouponIssueRequestStatus.ISSUED;
        this.userCouponId = userCouponId;
        this.processedAt = processedAt;
    }

    public void markSoldOut(ZonedDateTime processedAt) {
        checkNotProcessed();
        this.status = CouponIssueRequestStatus.SOLD_OUT;
        this.processedAt = processedAt;
    }

    public void markAlreadyIssued(Long userCouponId, ZonedDateTime processedAt) {
        checkNotProcessed();
        this.status = CouponIssueRequestStatus.ALREADY_ISSUED;
        this.userCouponId = userCouponId;
        this.processedAt = processedAt;
    }

    public void markFailed(String reason, ZonedDateTime processedAt) {
        checkNotProcessed();
        this.status = CouponIssueRequestStatus.FAILED;
        this.reason = reason;
        this.processedAt = processedAt;
    }

    private void checkNotProcessed() {
        if (isProcessed()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 처리된 발급 요청입니다.");
        }
    }

    private static ZonedDateTime requireRequestedAt(ZonedDateTime requestedAt) {
        if (requestedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 요청 일시는 비어있을 수 없습니다.");
        }
        return requestedAt;
    }
}
