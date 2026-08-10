package ru.itmo.nemat.weezzy.profile.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProfilePhotoProperties.class)
class ProfilePhotoConfiguration {
}
