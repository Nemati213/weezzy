package ru.itmo.nemat.weezzy.recommendation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecommendationProperties.class)
class RecommendationConfiguration {
}
