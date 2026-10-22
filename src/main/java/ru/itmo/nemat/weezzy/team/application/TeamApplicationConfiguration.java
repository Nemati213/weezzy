package ru.itmo.nemat.weezzy.team.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TeamApplicationProperties.class)
class TeamApplicationConfiguration {
}
