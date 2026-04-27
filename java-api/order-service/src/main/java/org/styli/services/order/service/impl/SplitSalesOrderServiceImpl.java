package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.exception.BadRequestException;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.KafkaBrazeHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.PendingPaymentToFailedResponse;
import org.styli.services.order.pojo.braze.BrazePendingPaymentEvent;
import org.styli.services.order.pojo.braze.BrazePendingPaymentPush;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.order.OrderEmailRequest;
import org.styli.services.order.pojo.order.UpdateOrderResponseDTO;
import org.styli.services.order.pojo.request.CustomCouponValidationV4Request;
import org.styli.services.order.pojo.request.GetPromosRequest;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.StoreCreditListRequest;
import org.styli.services.order.pojo.request.StoreCreditRequest;
import org.styli.services.order.pojo.request.UpdateOrderRequest;
import org.styli.services.order.pojo.response.BankCouponsresponse;
import org.styli.services.order.pojo.response.BankCouponsresponseBody;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.CustomerStoreCredit;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponse;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponseList;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.OmsOrderupdateresponse;
import org.styli.services.order.pojo.response.Coupon.ProductPromotions;
import org.styli.services.order.pojo.response.Coupon.ProductPromotionsDTO;
import org.styli.services.order.pojo.response.Order.CreditHistoryResponse;
import org.styli.services.order.pojo.response.Order.CustomerOrdersCountResponseDTO;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.response.Order.ReferalOrderData;
import org.styli.services.order.pojo.response.external.BankCouponData;
import org.styli.services.order.pojo.response.external.CustomBankCouponListResponse;
import org.styli.services.order.pojo.response.external.CustomCouponData;
import org.styli.services.order.pojo.response.external.CustomCouponListResponse;
import org.styli.services.order.pojo.response.external.ReferralOrderListResponse;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.service.*;
import org.styli.services.order.service.impl.child.GetFailedOrderList;
import org.styli.services.order.service.impl.child.GetOrderById;
import org.styli.services.order.service.impl.child.GetOrderCount;
import org.styli.services.order.service.impl.child.GetOrderList;
import org.styli.services.order.service.impl.child.GetStoreCreditHistory;
import org.styli.services.order.service.impl.child.OrderShipmentHelper;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.UtilityConstant;
import org.styli.services.order.utility.consulValues.FeatureBasedFlag;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;

import com.google.common.collect.Lists;

import io.grpc.netty.shaded.io.netty.util.internal.StringUtil;

@Component
public class SplitSalesOrderServiceImpl implements SplitSalesOrderService {

    private static final Log LOGGER = LogFactory.getLog(SalesOrderServiceImpl.class);

    public static final String PENDING_PAYMENT_ORDER_STATUS = "pending_payment";
    private static final String REFERRAL_SERVICE_OFF = "Referal Service off!";

    @Autowired
    SplitSalesOrderRepository splitSalesOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SplitSalesOrder> findByOrderId(Integer orderId) {
        return splitSalesOrderRepository.findByOrderId(orderId);
    }
}
