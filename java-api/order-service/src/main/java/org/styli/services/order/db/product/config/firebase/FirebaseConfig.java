package org.styli.services.order.db.product.config.firebase;

import java.io.File;
import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Firebase config
 * 
 * @author Chandan Behera
 *
 */
@Configuration
@Slf4j


public class FirebaseConfig {
	
	@Value("${gcp.project.id}")
	private String projectId;
	
	private static final String FIREBASE_CREDENTIAL = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

	@Primary
	@Bean
		void firebaseInit() throws IOException {
			log.info("Initializing Firebase Admin. with project ID " + projectId);
			FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault())
					.setProjectId(projectId)
					.build();
			if (FirebaseApp.getApps().isEmpty()) {
				FirebaseApp.initializeApp(options);
			}
			log.info("Firebase Admin Initialized!");
		}


}
