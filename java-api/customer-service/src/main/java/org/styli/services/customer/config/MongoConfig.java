package org.styli.services.customer.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDbFactory;



@Configuration
public class MongoConfig {
//	@Autowired
//	private Environment env;
//
//	@Bean(name = "uatMongoTemplate")
//	@Primary  
//	public MongoDbFactory mongoDbUatFactory() {
//		//MongoDbFactory mongoDbFactory = new SimpleMongoClientDbFactory(env.getProperty("spring.data.mongo.firstdb.uat.uri"));
//		return new SimpleMongoClientDbFactory(env.getProperty("spring.data.mongo.firstdb.uat.uri")); 
//	}
//	
//	@Bean(name = "devMongoTemplate")
//	public MongoDbFactory mongoDbDevFactory() {
//		//MongoDbFactory mongoDbFactory = new SimpleMongoClientDatabaseFactory(env.getProperty("spring.data.mongodb.newdb2.dev.uri"));
//		
//		return new SimpleMongoClientDbFactory(env.getProperty("spring.data.mongodb.newdb2.dev.uri")); 
//	}
//
//	@Bean
//	public MongoTemplate mongoUatDbTemplate() {
//		MongoTemplate mongoFirstDbTemplate = new MongoTemplate(mongoDbUatFactory());
//		return mongoFirstDbTemplate;
//	}
//	
//	@Bean
//	public MongoTemplate mongoDevDbTemplate() {
//		MongoTemplate mongoFirstDbTemplate = new MongoTemplate(mongoDbDevFactory());
//		return mongoFirstDbTemplate;
//	}
//	
	//abstract public MongoTemplate getMongoTemplate() throws Exception;
}
