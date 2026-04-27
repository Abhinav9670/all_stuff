package org.styli.services.customer.service;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import lombok.Getter;
import lombok.Setter;

@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties
// @ConfigurationProperties(locations =
// "classpath:application-errors.properties")

@PropertySource("classpath:application.properties")
@ConfigurationProperties(prefix = "magento")
@Getter
@Setter
public class ConfigProperties {

    private String magentoImageUrl;

    private String magentoCategoryImageUrl;

}
