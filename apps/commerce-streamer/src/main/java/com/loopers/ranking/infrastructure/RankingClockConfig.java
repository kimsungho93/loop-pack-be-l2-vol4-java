package com.loopers.ranking.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RankingClockConfig {

    @Bean
    public Clock rankingClock() {
        return Clock.systemUTC();
    }
}
