package com.loopers.ranking.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RankingCarryOverConfig {

    @Bean
    public Clock rankingCarryOverClock() {
        return Clock.systemUTC();
    }
}
