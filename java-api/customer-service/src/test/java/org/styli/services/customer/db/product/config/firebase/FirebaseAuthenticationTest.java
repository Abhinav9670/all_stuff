package org.styli.services.customer.db.product.config.firebase;

import org.mockito.InjectMocks;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

@SpringBootTest(classes = { FirebaseAuthenticationTest.class })
public class FirebaseAuthenticationTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	private FirebaseAuthentication firebaseAuthentication;

//    @Test
//    public void testVerifyToken() throws FirebaseAuthException {
//    	firebaseAuthentication.verifyToken(any());
//    }

}
