package org.styli.services.customer.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;

@DataJpaTest
@EntityScan("org.styli.services.customer.model")
@EnableJpaRepositories("org.styli.services.customer.repository")
@SpringJUnitConfig
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(locations = "classpath:test.properties")
public class StoreRepositoryTest {
	@Autowired
	private StoreRepository storeRepository;

	@BeforeMethod
	public void beforeMethod() {
	}

	@AfterMethod
	public void afterMethod() {
	}

	@BeforeClass
	public void beforeClass() {
	}

	@AfterClass
	public void afterClass() {
	}

	@BeforeTest
	public void beforeTest() {
	}

	@AfterTest
	public void afterTest() {
	}

	@BeforeSuite
	public void beforeSuite() {
	}

	@AfterSuite
	public void afterSuite() {
	}

//  @Test
//  public void findByStoreIdTest() {
//	  
//	  Store store = new Store();
//	  store.setStoreId(1);
//	  store.setName("ksa");
//	  store.setIsActive(1);
//	  storeRepository.save(store);
//	  
//	  Store storeresponse = storeRepository.findByStoreId(1);
//	  assertEquals("ksa", storeresponse.getName());
//  }
//
//  @Test
//  public void findByWebSiteIdTest() {
//    throw new RuntimeException("Test not implemented");
//  }
}
