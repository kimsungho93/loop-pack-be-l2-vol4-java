package com.loopers.ranking.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("commerce.ranking.cold-start")
public record RankingColdStartProperties(
    @DefaultValue("0.1") double carryOverRate
) {

    public RankingColdStartProperties {
        if (carryOverRate <= 0 || carryOverRate > 1) {
            throw new IllegalArgumentException("carryOverRate must be greater than 0 and less than or equal to 1");
        }
    }
}
