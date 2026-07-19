package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RankingScorePolicyTest {

    private final RankingScorePolicy policy = new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);

    @DisplayName("상품 조회 한 건은 0.1점으로 계산한다.")
    @Test
    void calculatesViewScore() {
        // act
        double score = policy.viewScore();

        // assert
        assertThat(score).isCloseTo(0.1, offset(1.0e-10));
    }

    @DisplayName("좋아요 한 건은 0.2점으로 계산한다.")
    @Test
    void calculatesLikeScore() {
        // act
        double score = policy.likeScore(1);

        // assert
        assertThat(score).isCloseTo(0.2, offset(1.0e-10));
    }

    @DisplayName("좋아요 취소 한 건은 -0.2점으로 계산한다.")
    @Test
    void calculatesUnlikeScore() {
        // act
        double score = policy.likeScore(-1);

        // assert
        assertThat(score).isCloseTo(-0.2, offset(1.0e-10));
    }

    @DisplayName("주문 금액은 10,000원 단위로 정규화한 뒤 0.7 가중치를 적용한다.")
    @Test
    void calculatesOrderScore() {
        // act
        double score = policy.orderScore(25_000);

        // assert
        assertThat(score).isCloseTo(1.75, offset(1.0e-10));
    }

    @DisplayName("Raw Metric 합계로 실시간 점수와 같은 총점을 계산한다.")
    @Test
    void calculatesTotalScore() {
        // act
        double score = policy.totalScore(10, 3, 25_000);

        // assert
        assertThat(score).isCloseTo(3.35, offset(1.0e-10));
    }
}
