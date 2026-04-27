package org.styli.services.customer.pojo.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StyliCoinsResponseTest {

	private StyliCoinsResponse styliCoinsResponseUnderTest;

	@BeforeEach
	void setUp() throws Exception {
		styliCoinsResponseUnderTest = new StyliCoinsResponse();
	}

	@Test
	void testEquals() {
		assertThat(styliCoinsResponseUnderTest.equals("o")).isFalse();
	}

	@Test
	void testCanEqual() {
		assertThat(styliCoinsResponseUnderTest.canEqual("other")).isFalse();
	}

	@Test
	void testHashCode() {
		assertThat(styliCoinsResponseUnderTest.hashCode()).isEqualTo(0);
	}

	@Test
	void testToString() {
		assertThat(styliCoinsResponseUnderTest.toString()).isEqualTo("result");
	}
}
