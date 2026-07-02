package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplate, Long> {

    Optional<CouponTemplate> findByIdAndDeletedAtIsNull(Long couponTemplateId);

    Page<CouponTemplate> findByDeletedAtIsNull(Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
        update CouponTemplate c
           set c.issuedCount = c.issuedCount + 1
         where c.id = :couponTemplateId
           and c.deletedAt is null
           and (c.totalQuantity.value is null or c.issuedCount < c.totalQuantity.value)
        """)
    int increaseIssuedCount(@Param("couponTemplateId") Long couponTemplateId);
}
