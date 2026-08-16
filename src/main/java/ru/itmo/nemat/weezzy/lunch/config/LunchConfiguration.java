package ru.itmo.nemat.weezzy.lunch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LunchProperties.class)
public class LunchConfiguration {
}
