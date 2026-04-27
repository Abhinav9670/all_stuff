package org.styli.services.customer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "org.styli.services.customer.repository.in.Wishlist",
        mongoTemplateRef = "indMongoTemplate")
public class SecondaryMongoConfig {

}