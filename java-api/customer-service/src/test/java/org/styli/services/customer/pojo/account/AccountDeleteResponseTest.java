package org.styli.services.customer.pojo.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

class AccountDeleteResponseTest {

	private AccountDeleteResponse accountDeleteResponseUnderTest;

	@BeforeMethod
	void setUp() {
		accountDeleteResponseUnderTest = new AccountDeleteResponse();

	}

	@Test
	void testEquals() {
		accountDeleteResponseUnderTest.hashCode();
		accountDeleteResponseUnderTest.toString();
		assertThat(accountDeleteResponseUnderTest.equals("o")).isFalse();
		assertThat(accountDeleteResponseUnderTest.canEqual("other")).isFalse();
	}

}
