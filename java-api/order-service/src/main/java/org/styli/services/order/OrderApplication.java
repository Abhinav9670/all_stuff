package org.styli.services.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableSwagger2
@EnableIntegration
public class OrderApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderApplication.class, args);
	}

	@Bean
	public Docket swaggerApi() {
		return new Docket(DocumentationType.SWAGGER_2).select()
				.apis(RequestHandlerSelectors.basePackage("org.styli.services.order.controller"))
				.paths(PathSelectors.any()).build().apiInfo(new ApiInfoBuilder().version("1.0").title("Order API")
						.description("Documentation Order API v1.0").build());
	}

	@Value("${cors.urls}")
	private String corsUrls;

	@Value("${cors.methods}")
	private String corsMethods;

	@Value("${env}")
	private String env;

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				String allowedOrigins;
				if (env.contains("dev") || env.contains("qa") || env.contains("qa01") || env.contains("uat")) {
					allowedOrigins = corsUrls + ",http://local.stylifashion.com:3000,https://local.stylifashion.com:3000" + ",http://localhost:3000"
							+ ",localhost:3000";
				} else {
					allowedOrigins = corsUrls;
				}
				registry.addMapping("/**").allowedOrigins(allowedOrigins.split(","));
			}
		};
	}
}
