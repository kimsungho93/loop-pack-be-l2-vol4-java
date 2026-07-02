package com.loopers.coupon.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.coupon.application.CouponIssueGate;
import com.loopers.coupon.application.CouponIssueGateResult;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
public class RedisCouponIssueGate implements CouponIssueGate {

    private static final String STOCK_KEY_PREFIX = "coupon:issue:stock:";
    private static final String ISSUED_USERS_KEY_PREFIX = "coupon:issue:users:";
    private static final long PASSED = 1L;
    private static final long DUPLICATE = 2L;
    private static final long SOLD_OUT = 3L;

    // 중복 확인 + lazy-init + 재고 차감을 한 번에 원자적으로 판정한다.
    // 재고 키가 만료 시각(PEXPIREAT)이 지나 사라진 경우 GET이 nil을 반환하므로 통과시키지 않는다.
    private static final RedisScript<Long> GATE_SCRIPT = RedisScript.of("""
        if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
            return 2
        end
        if redis.call('EXISTS', KEYS[1]) == 0 then
            redis.call('SET', KEYS[1], ARGV[2])
            redis.call('PEXPIREAT', KEYS[1], ARGV[3])
        end
        local stock = redis.call('GET', KEYS[1])
        if stock == false or tonumber(stock) <= 0 then
            return 3
        end
        redis.call('DECR', KEYS[1])
        redis.call('SADD', KEYS[2], ARGV[1])
        redis.call('PEXPIREAT', KEYS[2], ARGV[3])
        return 1
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCouponIssueGate(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public CouponIssueGateResult tryPass(Long userId, Long couponTemplateId, int totalQuantity, ZonedDateTime expiredAt) {
        Long result;
        try {
            result = redisTemplate.execute(
                GATE_SCRIPT,
                List.of(stockKey(couponTemplateId), issuedUsersKey(couponTemplateId)),
                String.valueOf(userId),
                String.valueOf(totalQuantity),
                String.valueOf(expiredAt.toInstant().toEpochMilli())
            );
        } catch (RuntimeException e) {
            log.error("Failed to evaluate coupon issue gate. couponTemplateId={}", couponTemplateId, e);
            throw failClosed();
        }
        return toResult(result, couponTemplateId);
    }

    private CouponIssueGateResult toResult(Long result, Long couponTemplateId) {
        if (result == null) {
            log.error("Coupon issue gate returned null. couponTemplateId={}", couponTemplateId);
            throw failClosed();
        }
        if (result == PASSED) {
            return CouponIssueGateResult.PASSED;
        }
        if (result == DUPLICATE) {
            return CouponIssueGateResult.DUPLICATE;
        }
        if (result == SOLD_OUT) {
            return CouponIssueGateResult.SOLD_OUT;
        }
        log.error("Coupon issue gate returned unknown value. couponTemplateId={}, result={}", couponTemplateId, result);
        throw failClosed();
    }

    // 게이트 장애 시 통과 대신 명시적으로 거절한다(fail-closed).
    private CoreException failClosed() {
        return new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }

    private String stockKey(Long couponTemplateId) {
        return STOCK_KEY_PREFIX + couponTemplateId;
    }

    private String issuedUsersKey(Long couponTemplateId) {
        return ISSUED_USERS_KEY_PREFIX + couponTemplateId;
    }
}
