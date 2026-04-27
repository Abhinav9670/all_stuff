package org.styli.services.customer.pojo.card.request;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.sql.Timestamp;
import java.util.ArrayList;

import org.styli.services.customer.pojo.card.response.CustomerCard;
import org.styli.services.customer.pojo.card.response.CustomerCardsResponseDTO;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.testng.annotations.Test;

public class CreateCardRequestTest {
	@Test
	public void testCreateCardRequest() {
		// Create a new CreateCardRequest object.
		CreateCardRequest createCardRequest = new CreateCardRequest();
		createCardRequest.setCustomerId(1);
		createCardRequest.setPublicHash("publicHash");
		createCardRequest.setPaymentMethodCode(PaymentMethodCodeENUM.MD_PAYFORT);
		createCardRequest.setType(TypeENUM.CARD);
		createCardRequest.setExpiresAt(new Timestamp(System.currentTimeMillis()));
		createCardRequest.setCardToken("cardToken");
		createCardRequest.setCardMask("cardMask");
		createCardRequest.setCardType("VISA");
		createCardRequest.setCardExp("12/2023");

		// Assert that the properties of the CreateCardRequest object are set correctly.
		assertEquals(createCardRequest.getCustomerId(), 1);
		assertEquals(createCardRequest.getPublicHash(), "publicHash");
		assertEquals(createCardRequest.getPaymentMethodCode().value, PaymentMethodCodeENUM.MD_PAYFORT.value);
		assertEquals(createCardRequest.getType().value, TypeENUM.CARD.value);
		assertNotNull(createCardRequest.getExpiresAt());
		assertEquals(createCardRequest.getCardToken(), "cardToken");
		assertEquals(createCardRequest.getCardMask(), "cardMask");
		assertEquals(createCardRequest.getCardType(), "VISA");
		assertEquals(createCardRequest.getCardExp(), "12/2023");
	}

	@Test
	public void testDeleteCardRequest() {
		DeleteCardRequest deleteCardRequest = new DeleteCardRequest();
		deleteCardRequest.setCustomerId(1);
		deleteCardRequest.setId(1);
		assertEquals(deleteCardRequest.getCustomerId(), 1);
		assertEquals(deleteCardRequest.getId(), 1);
	}

	@Test
	public void testCustomerCard() {
		// Create a new CustomerCard object.
		CustomerCard customerCard = new CustomerCard();
		customerCard.setId(1);
		customerCard.setCustomerId(1);
		customerCard.setPublicHash("publicHash");
		customerCard.setPaymentMethodCode("pay_fort");
		customerCard.setType("type");
		customerCard.setExpiresAt("10");
		customerCard.setCardToken("cardToken");
		customerCard.setCardMask("cardMask");
		customerCard.setCardType("VISA");
		customerCard.setCardExp("12/2023");
		customerCard.setActive(1);
		customerCard.setVisible(1);
		customerCard.setCardBin("cardBin");
		customerCard.setStoreId("storeId");

		// Assert that the properties of the CustomerCard object are set correctly.
		assertEquals(customerCard.getId(), 1);
		assertEquals(customerCard.getCustomerId(), 1);
		assertEquals(customerCard.getPublicHash(), "publicHash");
		assertEquals(customerCard.getPaymentMethodCode(), "pay_fort");
		assertEquals(customerCard.getType(), "type");
		assertEquals(customerCard.getExpiresAt(), "10");
		assertEquals(customerCard.getCardToken(), "cardToken");
		assertEquals(customerCard.getCardMask(), "cardMask");
		assertEquals(customerCard.getCardType(), "VISA");
		assertEquals(customerCard.getCardExp(), "12/2023");
		assertEquals(customerCard.getActive(), 1);
		assertEquals(customerCard.getVisible(), 1);
		assertEquals(customerCard.getCardBin(), "cardBin");
		assertEquals(customerCard.getStoreId(), "storeId");
	}

	@Test
	public void testCustomerCardsResponseDTO() {
		// Create a new CustomerCardsResponseDTO object.
		CustomerCardsResponseDTO customerCardsResponseDTO = new CustomerCardsResponseDTO();
		customerCardsResponseDTO.setStatus(true);
		customerCardsResponseDTO.setStatusCode("200");
		customerCardsResponseDTO.setStatusMsg("OK");
		customerCardsResponseDTO.setResponse(new ArrayList<CustomerCard>());
		customerCardsResponseDTO.setError(new ErrorType());
		customerCardsResponseDTO.getError().setErrorCode("1000");
		customerCardsResponseDTO.getError().setErrorMessage("An unexpected error occurred.");

		// Assert that the properties of the CustomerCardsResponseDTO object are set
		// correctly.
		assertEquals(customerCardsResponseDTO.getStatus(), true);
		assertEquals(customerCardsResponseDTO.getStatusCode(), "200");
		assertEquals(customerCardsResponseDTO.getStatusMsg(), "OK");
		assertEquals(customerCardsResponseDTO.getResponse().size(), 0);
		assertEquals(customerCardsResponseDTO.getError().getErrorCode(), "1000");
		assertEquals(customerCardsResponseDTO.getError().getErrorMessage(), "An unexpected error occurred.");
	}
}
