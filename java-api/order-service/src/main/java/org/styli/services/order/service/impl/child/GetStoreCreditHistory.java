package org.styli.services.order.service.impl.child;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.pojo.request.Order.OrderStoreCredit;
import org.styli.services.order.pojo.request.Order.OrderStoreCreditRequest;
import org.styli.services.order.pojo.response.Order.CreditHistoryResponse;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.utility.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class GetStoreCreditHistory {

    public CreditHistoryResponse get(OrderStoreCreditRequest request,
                                     AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository,
                                     StaticComponents staticComponents,
                                     OmsorderentityConverter omsorderentityConverter,
                                     AmastyStoreCreditRepository amastyStoreCreditRepository) {

        CreditHistoryResponse response = new CreditHistoryResponse();


        List<Stores> stores = Constants.getStoresList();
        Stores store = stores.stream()
                .filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
                .findAny()
                .orElse(null);

        if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
            response.setStatus(false);
            response.setStatusCode("202");
            response.setStatusMsg("Store not found!");
            return response;
        }

        List<OrderStoreCredit> storeCreditList = new ArrayList<OrderStoreCredit>();

        List<AmastyStoreCreditHistory> creditHistories = amastyStoreCreditHistoryRepository.findByCustomerIdOrderByHistoryIdDesc(request.getCustomerId());
        DecimalFormat df = new DecimalFormat(".##");

//        API-1649
        BigDecimal returnableAmountBalance = BigDecimal.ZERO;
        BigDecimal totalAmountBalance = BigDecimal.ZERO;
        List<AmastyStoreCredit> storeCredits = amastyStoreCreditRepository.findByCustomerId(request.getCustomerId());
        if (ObjectUtils.isNotEmpty(storeCredits)) {
            AmastyStoreCredit storeCredit = storeCredits.get(0);
            if (ObjectUtils.isNotEmpty(storeCredit.getReturnableAmount()))
                returnableAmountBalance = storeCredit.getReturnableAmount();
            if (ObjectUtils.isNotEmpty(storeCredit.getStoreCredit()))
                totalAmountBalance = storeCredit.getStoreCredit();
        }

        BigDecimal convertedTotalAmount = totalAmountBalance
                .divide(store.getCurrencyConversionRate(), 4, RoundingMode.HALF_UP);
        BigDecimal convertedReturnableAmount = returnableAmountBalance
                .divide(store.getCurrencyConversionRate(), 4, RoundingMode.HALF_UP);

        response.setReturnableAmount(omsorderentityConverter.parseNullStr(convertedReturnableAmount));
        response.setTotalAmount(omsorderentityConverter.parseNullStr(convertedTotalAmount));


        if(CollectionUtils.isNotEmpty(creditHistories)) {

            for (AmastyStoreCreditHistory styliCredit : creditHistories) {

                OrderStoreCredit storeCredit = new OrderStoreCredit();

                if(null != styliCredit.getCreatedAt()) {

                    String time = omsorderentityConverter.convertTimeZone(styliCredit.getCreatedAt(), request.getStoreId());

                    storeCredit.setDate(time);

                }if(null != styliCredit.getActionData() && styliCredit.getActionData().contains("\"")) {

                    storeCredit.setOrderId(StringUtils.substringBetween(styliCredit.getActionData(), "[\"", "\"]"));

                }
                	
            storeCredit.setComment(styliCredit.getMessage());
                
           if(null != styliCredit.getAction() && styliCredit.getAction().equals(1)) {
        	   
        	   storeCredit.setActionData("Changed By Admin");
           }else if(null != styliCredit.getAction() && styliCredit.getAction().equals(2)) {

               storeCredit.setActionData("Bank transfer");
           }else if(null != styliCredit.getAction() && styliCredit.getAction().equals(6)) {
        	   
        	   storeCredit.setActionData("Referral");
           }else if(null != styliCredit.getAction() && styliCredit.getAction().equals(7)) {
        	   
        	   storeCredit.setActionData("Admin Refund");
           }else if(null != styliCredit.getAction() && styliCredit.getAction().equals(8)) {
        	   
        	   storeCredit.setActionData("Finance Bulk Update");
           } else if(null != styliCredit.getAction() && styliCredit.getAction().equals(9)) {
               storeCredit.setActionData("braze_promo");
           }

                if (StringUtils.isBlank(storeCredit.getOrderId()) || storeCredit.getOrderId().equals("null")) {
                    storeCredit.setOrderId(storeCredit.getActionData());
                }
                
                if(null != styliCredit.getStoreCreditBalance()) {

                    BigDecimal convertedStoreCreditBalance = styliCredit.getStoreCreditBalance()
                            .divide(store.getCurrencyConversionRate(), 4, RoundingMode.HALF_UP);
                    
                    storeCredit.setBalance(df.format(convertedStoreCreditBalance));

                }if(null != styliCredit.getDifference()) {

                    BigDecimal convertedStoreCreditDiff = styliCredit.getDifference()
                            .divide(store.getCurrencyConversionRate(), 4, RoundingMode.HALF_UP);
                    storeCredit.setAmount(df.format(convertedStoreCreditDiff));
                }if(null != styliCredit.getDeduct() && styliCredit.getDeduct().equals(0)) {

                    storeCredit.setIsCredit(true);
                }else {

                    storeCredit.setIsCredit(false);
                }

                storeCreditList.add(storeCredit);
            }


        }else {

            response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg("no styli credit/debit found");
        }

        response.setResponse(storeCreditList);
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("fetched successfully");

        return response;
    }

}
