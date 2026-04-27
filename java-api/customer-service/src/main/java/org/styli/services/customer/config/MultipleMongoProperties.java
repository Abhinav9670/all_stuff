package org.styli.services.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Primary
@ConfigurationProperties(prefix = "spring.data.mongodb")
public class MultipleMongoProperties {

    private MongoProperties gcc;
    private MongoProperties in;

    // Getters and Setters
    public MongoProperties getGcc() {
        return gcc;
    }

    public void setGcc(MongoProperties gcc) {
        this.gcc = gcc;
    }

    public MongoProperties getIn() {
        return in;
    }

    public void setIn(MongoProperties in) {
        this.in = in;
    }
}
