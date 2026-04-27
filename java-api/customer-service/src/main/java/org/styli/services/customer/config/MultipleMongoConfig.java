package org.styli.services.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.mongodb.client.MongoClients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MultipleMongoProperties.class)
@Slf4j
public class MultipleMongoConfig {

    private final MultipleMongoProperties mongoProperties;

    @Primary
    @Bean(name = "gccMongoTemplate")
    public MongoTemplate primaryMongoTemplate() throws Exception {
        return new MongoTemplate(primaryFactory());
    }

    @Primary
    @Bean
    public MongoDatabaseFactory primaryFactory() throws Exception {
        String uri = mongoProperties.getGcc().getUri();
        String database = mongoProperties.getGcc().getDatabase();

        if (database == null || database.isEmpty()) {
            log.error("Primary database name must not be empty!");
            throw new IllegalArgumentException("Primary database name must not be empty!");
        }

        log.info("Connecting to primary database: {} at URI: {}", database, uri);
        return new SimpleMongoClientDatabaseFactory(MongoClients.create(uri), database);
    }

    @Bean(name = "indMongoTemplate")
    public MongoTemplate secondaryMongoTemplate() throws Exception {
        return new MongoTemplate(secondaryFactory());
    }

    @Bean
    public MongoDatabaseFactory secondaryFactory() throws Exception {
        String uri = mongoProperties.getIn().getUri();
        String database = mongoProperties.getIn().getDatabase();

        if (database == null || database.isEmpty()) {
            log.error("Secondary database name must not be empty!");
            throw new IllegalArgumentException("Secondary database name must not be empty!");
        }

        log.info("Connecting to secondary database: {} at URI: {}", database, uri);
        return new SimpleMongoClientDatabaseFactory(MongoClients.create(uri), database);
    }
}