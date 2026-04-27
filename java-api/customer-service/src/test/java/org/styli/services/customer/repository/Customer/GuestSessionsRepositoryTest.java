package org.styli.services.customer.repository.Customer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;

@SpringBootTest(classes = { GuestSessionsRepository.class })

@DataJpaTest
//@Import(GuestSessionsRepository.class)
public class GuestSessionsRepositoryTest {
	@Autowired
	private GuestSessionsRepository guestSessionrepo;

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

//	@Test
//	public void findByDeviceIdTest() {
//		GuestSessions gs=new GuestSessions();
//		gs.setEntityId(1);
//		gs.setDeviceId("nksncnsc");
//		gs.setUuid("hcdscs");
//		
//		guestSessionrepo.save(gs);
//		
//		GuestSessions res = guestSessionrepo.findByDeviceId("nksncnsc");
//		assertEquals(res.getEntityId(), 1);
//	}
}
