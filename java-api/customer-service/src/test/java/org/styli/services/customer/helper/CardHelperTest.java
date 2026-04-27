package org.styli.services.customer.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataAccessException;
import org.styli.services.customer.model.VaultPaymentToken;
import org.styli.services.customer.pojo.card.CustomerCardDetails;
import org.styli.services.customer.pojo.card.request.CreateCardRequest;
import org.styli.services.customer.pojo.card.request.DeleteCardRequest;
import org.styli.services.customer.pojo.card.request.PaymentMethodCodeENUM;
import org.styli.services.customer.pojo.card.request.TypeENUM;
import org.styli.services.customer.pojo.card.response.CustomerCard;
import org.styli.services.customer.pojo.card.response.CustomerCardsResponseDTO;
import org.styli.services.customer.repository.VaultPaymentTokenRepository;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.gson.Gson;

public class CardHelperTest {

	@InjectMocks
	private CardHelper cardHelper;

	@Mock
	private VaultPaymentTokenRepository vaultPaymentTokenRepository;
	private List<VaultPaymentToken> tokens;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		tokens = new ArrayList<>();
		VaultPaymentToken token = new VaultPaymentToken();
		token.setEntityId(1);
		token.setCustomerId(1);
		tokens.add(token);
	}

	@Test
	public void testGetCustomerCards() throws Exception {
		// Arrange
		Integer customerId = 1;
		VaultPaymentToken v1 = new VaultPaymentToken();
		VaultPaymentToken v2 = new VaultPaymentToken();
		v1.setEntityId(1);
		v1.setGatewayToken("token");
		CustomerCardDetails details = new CustomerCardDetails();
		details.setFirstname("first");
		details.setLastname("last");
		v1.setDetails(new Gson().toJson(details));

		v1.setExpiresAt(new Timestamp(100l));
		v2.setEntityId(1);
		v2.setGatewayToken("token");
		v2.setDetails(new Gson().toJson(details));

		v2.setExpiresAt(new Timestamp(100l));
		List<VaultPaymentToken> vaultPaymentTokens = Arrays.asList(v1, v2);
		when(vaultPaymentTokenRepository.findByCustomerIdAndActiveAndVisible(customerId, 1, 1))
				.thenReturn(vaultPaymentTokens);

		CustomerCard card1 = new CustomerCard();
		CustomerCard card2 = new CustomerCard();
		List<CustomerCard> cards = Arrays.asList(card1, card2);
//        when(cardHelper.getCustomerCards(customerId)).thenReturn(new CustomerCardsResponseDTO());

		// Act
		CustomerCardsResponseDTO response = cardHelper.getCustomerCards(customerId);

		// Assert
		Assert.assertEquals(response.getStatusCode(), "200");
		Assert.assertEquals(response.getStatusMsg(), "Customer Cards fetched successfully!");
		Assert.assertNotNull(response.getResponse());
	}

	@Test
	public void testCreateCard() throws Exception {
		// Arrange
		CreateCardRequest request = new CreateCardRequest();
		request.setCustomerId(1);
		request.setCardType("VISA");
		request.setCardExp("12/23");
		request.setCardMask("**** **** **** 1234");
		request.setCardToken("tok_1234567890");
		request.setExpiresAt(null);
		request.setPaymentMethodCode(PaymentMethodCodeENUM.MD_PAYFORT);
		request.setType(TypeENUM.CARD);

		when(vaultPaymentTokenRepository.saveAndFlush(any())).thenReturn(new VaultPaymentToken());

		// Act
		CustomerCardsResponseDTO response = cardHelper.createCard(request);

		// Assert
		Assert.assertNull(response.getError());
		Assert.assertEquals(response.getStatusCode(), "200");
		verify(vaultPaymentTokenRepository, times(1)).saveAndFlush(any());
	}

	@Test
	public void testDeleteCard() {
		// Create mock data
		int entityId = 1;
		int customerId = 1;
		DeleteCardRequest request = new DeleteCardRequest();
		request.setId(entityId);
		request.setCustomerId(customerId);

		// Mock repository method calls
		when(vaultPaymentTokenRepository.findByEntityIdAndCustomerId(entityId, customerId)).thenReturn(tokens);

		// Call the method to be tested
		CustomerCardsResponseDTO response = cardHelper.deleteCard(request);

		// Verify the repository method calls
		verify(vaultPaymentTokenRepository).findByEntityIdAndCustomerId(entityId, customerId);
		verify(vaultPaymentTokenRepository).deleteByEntityId(entityId, customerId);

		// Verify the response
		Assert.assertNotNull(response);
		Assert.assertEquals(response.getStatusCode(), "200");
	}

	@Test(priority = 1)
	public void testDeleteCardWithInvalidRequest() {
		// Create mock data
		int entityId = 1;
		int customerId = 1;
		DeleteCardRequest request = new DeleteCardRequest();
		request.setId(2);
		request.setCustomerId(3);

		// Mock repository method calls
		// when(vaultPaymentTokenRepository.findByEntityIdAndCustomerId(entityId,
		// customerId)).thenReturn(null);

		// Call the method to be tested
		CustomerCardsResponseDTO response = cardHelper.deleteCard(request);

		// Verify the repository method calls
		// verify(vaultPaymentTokenRepository).findByEntityIdAndCustomerId(entityId,
		// customerId);
//        v÷erify(vaultPaymentTokenRepository, never()).deleteByEntityId(entityId, customerId);

		// Verify the response
		Assert.assertNotNull(response);
		Assert.assertEquals(response.getStatusCode(), "205");
		Assert.assertEquals(response.getStatusMsg(), "Invalid request!!");
		Assert.assertNull(response.getResponse());
	}

	@Test
	public void testDeleteCardWithDataAccessException() {
		// Create mock data
		int entityId = 1;
		int customerId = 1;
		DeleteCardRequest request = new DeleteCardRequest();
		request.setId(entityId);
		request.setCustomerId(customerId);

		// Mock repository method calls
		when(vaultPaymentTokenRepository.findByEntityIdAndCustomerId(entityId, customerId))
				.thenThrow(new DataAccessException("Error while deleting card") {
				});
		CustomerCardsResponseDTO response = cardHelper.deleteCard(request);
		Assert.assertNotNull(response);
		Assert.assertEquals(response.getStatusCode(), "201");
		Assert.assertNull(response.getResponse());
	}

}
