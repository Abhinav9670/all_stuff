package org.styli.services.customer.service.impl;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.customer.helper.CardHelper;
import org.styli.services.customer.pojo.PrintLogInfoRequest;
import org.styli.services.customer.pojo.card.request.CreateCardRequest;
import org.styli.services.customer.pojo.card.request.DeleteCardRequest;
import org.styli.services.customer.pojo.card.response.CustomerCardsResponseDTO;
import org.styli.services.customer.service.SalesOrderService;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SalesOrderServiceImpl implements SalesOrderService {
    private static final Log LOGGER = LogFactory.getLog(SalesOrderServiceImpl.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    CardHelper cardHelper;

    @Override
    public CustomerCardsResponseDTO createCard(CreateCardRequest request) {
        return cardHelper.createCard(request);

    }

    @Override
    public CustomerCardsResponseDTO deleteCard(DeleteCardRequest request) {
        return cardHelper.deleteCard(request);

    }

    @Override
    public CustomerCardsResponseDTO getCustomerCards(Integer customerId) {
        return cardHelper.getCustomerCards(customerId);
    }

    @Override
    public void printLogInfos(@NotNull @Valid PrintLogInfoRequest request) {
            String requestString = null;
            try {
                requestString = mapper.writeValueAsString(request);
            } catch (Exception e) {
                requestString = request.toString();
            }
            LOGGER.info("Print request: "+ requestString);
    }

    /**
     * Deletes all expired customer cards from the database.
     *
     * This method delegates the operation to the `CardHelper` class, which interacts
     * with the `VaultPaymentTokenRepository` to remove expired payment tokens. It
     * returns a response indicating the success or failure of the operation.
     *
     * @return CustomerCardsResponseDTO containing the status, status code, and message
     *         of the operation.
     */
    @Override
    public CustomerCardsResponseDTO deactivateAllExpiryCards(Integer customerId,Integer expiryInHours) {
        return cardHelper.deactivateAllExpiryCards(customerId,expiryInHours);
    }

}
