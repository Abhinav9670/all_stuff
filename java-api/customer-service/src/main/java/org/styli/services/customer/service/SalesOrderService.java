package org.styli.services.customer.service;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.styli.services.customer.pojo.PrintLogInfoRequest;
import org.styli.services.customer.pojo.card.request.*;
import org.styli.services.customer.pojo.card.response.*;

@Service
public interface SalesOrderService {

    public CustomerCardsResponseDTO createCard(CreateCardRequest request);

    public CustomerCardsResponseDTO deleteCard(DeleteCardRequest request);

    public CustomerCardsResponseDTO getCustomerCards(Integer customerId);

    public void printLogInfos(@NotNull @Valid PrintLogInfoRequest request);

    public CustomerCardsResponseDTO deactivateAllExpiryCards(Integer customerId,Integer expiryInHours);

}
