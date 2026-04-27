package org.styli.services.customer.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.styli.services.customer.model.VaultPaymentToken;
import org.styli.services.customer.pojo.card.CustomerCardDetails;
import org.styli.services.customer.pojo.card.request.*;
import org.styli.services.customer.pojo.card.response.*;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.repository.VaultPaymentTokenRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class CardHelper {

    private static final Log LOGGER = LogFactory.getLog(CardHelper.class);

    @Autowired
    VaultPaymentTokenRepository vaultPaymentTokenRepository;
    
    private static final String ERROR = "Error: ";

    public CustomerCardsResponseDTO getCustomerCards(Integer customerId) {
    	
        CustomerCardsResponseDTO resp = new CustomerCardsResponseDTO();

        
		if (null == customerId) {

			resp.setStatus(false);
			resp.setStatusCode("206");
			resp.setStatusMsg("customer id is null!");

			return resp;
		}

        ObjectMapper mapper = new ObjectMapper();

        List<VaultPaymentToken> vaultPaymentTokens = vaultPaymentTokenRepository
                .findByCustomerId(customerId);
        List<CustomerCard> cards = new ArrayList<>();

        for (VaultPaymentToken vaultPaymentToken : vaultPaymentTokens) {
            CustomerCard cardObj = new CustomerCard();

            BeanUtils.copyProperties(vaultPaymentToken, cardObj);
            cardObj.setId(vaultPaymentToken.getEntityId());
            if (vaultPaymentToken.getExpiresAt() != null) {
                cardObj.setExpiresAt(vaultPaymentToken.getExpiresAt().toString());
            }
            cardObj.setCardToken(vaultPaymentToken.getGatewayToken());

            String details = vaultPaymentToken.getDetails();

            try {
                CustomerCardDetails cardDetails = mapper.readValue(details, CustomerCardDetails.class);
                cardObj.setCardMask(cardDetails.getMaskedCC());
                cardObj.setCardType(cardDetails.getType());
                cardObj.setCardExp(cardDetails.getExpirationDate());
                cardObj.setCardBin(cardDetails.getCardBin());
                cardObj.setStoreId(cardDetails.getStoreId());
            } catch (IOException e) {
                LOGGER.info("Error while fetching customer cards. customer id: " + customerId + " card id: "
                        + vaultPaymentToken.getEntityId());
                continue;
            }

            LOGGER.info("cardObj " + cardObj);
            cards.add(cardObj);
        }

        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg("Customer Cards fetched successfully!");
        resp.setResponse(cards);

        return resp;

    }

    public CustomerCardsResponseDTO createCard(CreateCardRequest request) {

        CustomerCardsResponseDTO resp = new CustomerCardsResponseDTO();

        VaultPaymentToken vaultPaymentToken = new VaultPaymentToken();

        BeanUtils.copyProperties(request, vaultPaymentToken);

        // Set rest of the properties
        vaultPaymentToken.setGatewayToken(request.getCardToken());
        vaultPaymentToken.setActive(1);
        vaultPaymentToken.setVisible(1);
        vaultPaymentToken.setExpiresAt(request.getExpiresAt());
        vaultPaymentToken.setPaymentMethodCode(request.getPaymentMethodCode().getValue());
        vaultPaymentToken.setType(request.getType().getValue());

        // Prepare java object for details property
        CustomerCardDetails cardDetails = new CustomerCardDetails();
        cardDetails.setType(request.getCardType());
        cardDetails.setExpirationDate(request.getCardExp());
        cardDetails.setMaskedCC(request.getCardMask());

        ObjectMapper mapper = new ObjectMapper();

        // Generate details json string from java object
        try {
            String details = mapper.writeValueAsString(cardDetails);
            vaultPaymentToken.setDetails(details);
        } catch (JsonProcessingException e) {
            resp.setStatus(false);
            resp.setStatusCode("202");
            resp.setStatusMsg(ERROR + e.getMessage());

            ErrorType error = new ErrorType();
            error.setErrorCode("202");
            error.setErrorMessage(e.getMessage());

            resp.setError(error);
            return resp;
        }

        LOGGER.info("vaultPaymentToken " + vaultPaymentToken);

        try {
            vaultPaymentTokenRepository.saveAndFlush(vaultPaymentToken);
        } catch (DataAccessException e) {
            resp.setStatus(false);
            resp.setStatusCode("201");
            resp.setStatusMsg(ERROR + e.getMessage());

            ErrorType error = new ErrorType();
            error.setErrorCode("201");
            error.setErrorMessage(e.getMessage());

            resp.setError(error);
            return resp;
        }

        return request.getCustomerId() != null ? getCustomerCards(request.getCustomerId()) : null;

    }

    public CustomerCardsResponseDTO deleteCard(DeleteCardRequest request) {
    	
		CustomerCardsResponseDTO resp = new CustomerCardsResponseDTO();

        try {
        	List<VaultPaymentToken> card = vaultPaymentTokenRepository.findByEntityIdAndCustomerId(request.getId() , request.getCustomerId());
        	
        	if(CollectionUtils.isEmpty(card)) {
        		resp.setStatus(false);
    			resp.setStatusCode("205");
    			resp.setStatusMsg("Invalid request!!");

    
    			return resp;
        		
        	}
            vaultPaymentTokenRepository.deleteByEntityId(request.getId() , request.getCustomerId());
		} catch (DataAccessException e) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg(ERROR + e.getMessage());

			ErrorType error = new ErrorType();
			error.setErrorCode("201");
			error.setErrorMessage(e.getMessage());

			resp.setError(error);
			return resp;
		}
        return request.getCustomerId() != null ? getCustomerCards(request.getCustomerId()) : null;
    }

    /**
     * Deactivate all expired customer cards from the database.
     *
     * This method interacts with the `VaultPaymentTokenRepository` to remove all
     * expired payment tokens. If the operation is successful, it returns a response
     * indicating success. In case of a failure, it captures the exception, logs the
     * error, and returns an appropriate error response.
     *
     * @return CustomerCardsResponseDTO containing the status, status code, and message
     *         of the operation.
     */
    public CustomerCardsResponseDTO deactivateAllExpiryCards(Integer customerId,Integer expiryInHours) {
        CustomerCardsResponseDTO resp = new CustomerCardsResponseDTO();
        try {
            LOGGER.info("In deactivateAllExpiryCards : customerId: " + customerId+" expiryInHours: " + expiryInHours);
            int count = vaultPaymentTokenRepository.deactivateAllExpiredTokens(customerId,expiryInHours);
            LOGGER.info("In deactivateAllExpiryCards : Expired cards made deactive: " + count);
        } catch (DataAccessException e) {
            resp.setStatus(false);
            resp.setStatusCode("201");
            resp.setStatusMsg(ERROR + e.getMessage());

            ErrorType error = new ErrorType();
            error.setErrorCode("201");
            error.setErrorMessage(e.getMessage());

            resp.setError(error);
            return resp;
        }
        //If success
        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg("All expired cards deactivated successfully!");

        return resp;
    }
}
