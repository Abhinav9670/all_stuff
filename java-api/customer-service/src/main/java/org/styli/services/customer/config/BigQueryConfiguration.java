package org.styli.services.customer.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;

@Configuration
public class BigQueryConfiguration {

	@Value("${bigquery.project-id}")
	private String bigQueryProjectId;

	@Bean
	BigQuery bigQuery() throws IOException {
		GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
		return BigQueryOptions.newBuilder().setCredentials(credentials).setProjectId(bigQueryProjectId).build()
				.getService();
	}

}
