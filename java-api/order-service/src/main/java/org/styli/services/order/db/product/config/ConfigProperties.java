package org.styli.services.order.db.product.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties
//@ConfigurationProperties(locations = "classpath:application-errors.properties")

@PropertySource("classpath:application.properties")
@ConfigurationProperties(prefix = "magento")
@Getter
@Setter
public class ConfigProperties {

    private String magentoImageUrl;

    private String magentoCategoryImageUrl;


}
