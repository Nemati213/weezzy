package ru.itmo.nemat.weezzy.team.recommendation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TeamRecommendationProperties.class)
class TeamRecommendationConfiguration {
}
