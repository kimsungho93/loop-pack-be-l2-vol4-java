package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingScorePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RankingScoreConfig {

    @Bean
    public RankingScorePolicy rankingScorePolicy(RankingScoreProperties properties) {
        return new RankingScorePolicy(
            properties.viewWeight(),
            properties.likeWeight(),
            properties.orderWeight(),
            properties.orderAmountUnit()
        );
    }

    @Bean
    public Clock rankingClock() {
        return Clock.systemUTC();
    }
}
