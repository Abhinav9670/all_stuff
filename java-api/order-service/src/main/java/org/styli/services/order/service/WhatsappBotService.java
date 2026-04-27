package org.styli.services.order.service;

import org.springframework.stereotype.Service;
import org.styli.services.order.pojo.GenericApiResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileReturnDetailResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileShipmentListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileShipmentDetailResponse;

import java.util.Map;

/**
 * Created on 27-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */


@Service
public interface WhatsappBotService {


    public GenericApiResponse<MobileOrderListResponse> getMobileOrderList(
            MobileOrderListRequest requestBody, Map<String, String> requestHeader, String authorizationToken);


    public GenericApiResponse<MobileOrderDetailResponse> getMobileOrderDetails(
            MobileOrderDetailRequest requestBody, Map<String, String> requestHeader, String authorizationToken);
    
	public GenericApiResponse<MobileOrderListResponse> getMobileReturnList(MobileOrderListRequest requestBody,
			String authorizationToken, boolean unPicked);

	public GenericApiResponse<MobileReturnDetailResponse> getMobileReturnDetails(MobileOrderDetailRequest requestBody,
			String authorizationToken);

	public GenericApiResponse<MobileShipmentListResponse> getMobileShipmentList(
			MobileOrderListRequest requestBody, Map<String, String> requestHeader, String authorizationToken);

	public GenericApiResponse<MobileShipmentDetailResponse> getMobileShipmentDetails(
			MobileOrderDetailRequest requestBody, Map<String, String> requestHeader, String authorizationToken);
}
