package org.styli.services.customer;

import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SpringBootTest(classes = { Slf4jMDCFilterConfigurationTest.class })
public class Slf4jMDCFilterConfigurationTest extends AbstractTestNGSpringContextTests {
	@InjectMocks
	Slf4jMDCFilterConfiguration config;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void servletRegistrationBeanTest() {
		config.servletRegistrationBean();

	}
}
