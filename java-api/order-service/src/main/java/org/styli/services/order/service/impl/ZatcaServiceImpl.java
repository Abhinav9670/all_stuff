package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.*;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.zatca.AccountingSupplierParty;
import org.styli.services.order.pojo.zatca.InvoiceLine;
import org.styli.services.order.pojo.zatca.ZatcaConfig;
import org.styli.services.order.pojo.zatca.ZatcaInvoice;
import org.styli.services.order.pojo.zatca.ZatcaInvoiceResponse;
import org.styli.services.order.pojo.zatca.bulk.BulkInvoiceResponse;
import org.styli.services.order.pojo.zatca.bulk.EInvoicesListRes;
import org.styli.services.order.pojo.zatca.bulk.ZatcaBulkRequest;
import org.styli.services.order.pojo.zatca.bulk.ZatcaInvoiceBulkSingle;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.service.EmailService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.HashSet;

@Service
public class ZatcaServiceImpl {

	public static final String PAYABLE_AMOUNT = "Payable Amount";
	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	SalesInvoiceRepository salesInvoiceRepository;

	@Autowired
	SubSalesOrderItemRepository subSalesOrderItemRepository;

	@Autowired
	SalesOrderItemRepository salesOrderItemRepository;

	@Autowired
	SplitSalesOrderItemRepository splitSalesOrderItemRepository;

	@Autowired
	SalesCreditmemoItemRepository salesCreditmemoItemRepository;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	SalesOrderAddressRepository salesOrderAddressRepository;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	EmailService emailService;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	MulinHelper mulinHelper;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Autowired
	PrepaidRefundHelper prepaidRefundHelper;

	@Autowired
	RtoZatcaHelper rtoZatcaHelper;

	@Value("${env}")
	private String env;

	private static final Log LOGGER = LogFactory.getLog(ZatcaServiceImpl.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	public static final String DATE_FORMAT= "yyyy-MM-dd";
	public static final String TIME_FORMAT= "HH:mm:ss";

	// @Async("asyncExecutor")
	public void syncBulk(List<String> IncrementList, String type) {
		try {
			if (type.equals("INVOICE")) {
				for (String incrementId : IncrementList) {
					SalesOrder order = salesOrderRepository.findByIncrementId(incrementId);
					sendZatcaInvoice(order, false, null);
				}
			} else if (type.equals("CREDIT_MEMO")) {
				for (String incrementId : IncrementList) {
					SalesCreditmemo salesCreditMemo = salesCreditmemoRepository.findByIncrementId(incrementId);
					SalesOrder order = salesOrderRepository.findByEntityId(salesCreditMemo.getOrderId());
					SalesInvoice invoice = order.getSalesInvoices().stream().findFirst().orElse(null);
					sendZatcaCreditMemo(salesCreditMemo, invoice, order, null, false, null, null, null, null);
				}
			}
		} catch (Exception e) {
			LOGGER.error("createShipmentZatca Exception!" + e.getMessage());
		}

	}

	public void sendZatcaInvoice(SalesOrder order, Boolean returnFlag, AmastyRmaRequest request) {
		ZatcaConfig zatcaConfig = Constants.orderCredentials.getZatcaConfig();
		SalesOrder orderZatca = salesOrderRepository.findByIncrementId(order.getIncrementId());
		SalesInvoice invoiceZatca = null;
		ZatcaInvoice zatcaInvoice = null;

		if (returnFlag == true && request != null) {

			zatcaInvoice = getSecondReturnInvoiceData(orderZatca, zatcaConfig, request);

		} else {
			invoiceZatca = order.getSalesInvoices().stream().findFirst().orElse(null);
			zatcaInvoice = generateZatcaInvoice(invoiceZatca, orderZatca, zatcaConfig, false);
		}

		if (null != zatcaInvoice) {

			try {
				String URL = zatcaConfig.getBaseUrl() + "/v2/einvoices/generate/async";

				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
				requestHeaders.add("x-cleartax-auth-token", zatcaConfig.getClearTaxToken());
				requestHeaders.add("vat", zatcaConfig.getVatNo());

				HttpEntity<ZatcaInvoice> requestBody = new HttpEntity<>(zatcaInvoice, requestHeaders);
				LOGGER.info(
						"Zatca Body : " + orderZatca.getEntityId() + " : " + mapper.writeValueAsString(zatcaInvoice));
				ResponseEntity<ZatcaInvoiceResponse> response = restTemplate.exchange(URL, HttpMethod.POST, requestBody,
						ZatcaInvoiceResponse.class);

				if (response.getStatusCode() == HttpStatus.OK) {
					if (response.getBody() != null && returnFlag) {
						request.setZatcaQrCode(response.getBody().getQrCode());
						request.setZatcaDetails(mapper.writeValueAsString(zatcaInvoice));
					} else {
						invoiceZatca.setZatcaQRCode(response.getBody().getQrCode());
						invoiceZatca.setZatcaStatus(response.getBody().getInvoiceStatus());
						salesInvoiceRepository.saveAndFlush(invoiceZatca);
					}
				}
			} catch (Exception e) {
				if (!returnFlag && request == null && invoiceZatca != null) {
					invoiceZatca.setZatcaStatus("FAILED");
					salesInvoiceRepository.saveAndFlush(invoiceZatca);
				}

				LOGGER.error("exception occoured during Zatca:" + e.getMessage());
			}
		}
	}

	// @Async("asyncExecutor")
	public void sendZatcaCreditMemo(SalesCreditmemo memo, SalesInvoice invoice, SalesOrder order, SplitSalesOrder splitSalesOrder, boolean isRto, Stores store, BigDecimal taxFactor, List<SalesCreditmemoItem> memoItems, BigDecimal totalTaxableAmount) {

		if (null == invoice || null == invoice.getZatcaStatus()) {
			LOGGER.info("Creditmemo had no related invoice");
			return;
		}
		String incrementId = order != null ? order.getIncrementId() : splitSalesOrder.getIncrementId();

		Integer entityId = order != null ? order.getEntityId() : splitSalesOrder.getEntityId();
		
		ZatcaConfig zatcaConfig = Constants.orderCredentials.getZatcaConfig();

		SalesCreditmemo memoZatca = salesCreditmemoRepository.findByIncrementId(memo.getIncrementId());
		SalesOrder orderZatca = null;
		SalesInvoice invoiceZatca = null;
		
		if (order != null) {
			// For regular orders, use the order's increment ID
			orderZatca = salesOrderRepository.findByIncrementId(incrementId);
			invoiceZatca = orderZatca != null ? orderZatca.getSalesInvoices().stream().findFirst().orElse(null) : null;
		} else {
			// For split orders, use the main order's increment ID and get invoice from split order
			invoiceZatca = splitSalesOrder.getSalesInvoices().stream().findFirst().orElse(null);
		}

		ZatcaInvoice zatcaInvoice = generateZatcaCreditMemo(memoZatca, invoiceZatca, orderZatca, splitSalesOrder, zatcaConfig);

		if(isRto && store != null && taxFactor != null && memoItems != null && totalTaxableAmount != null){
			zatcaInvoice = rtoZatcaHelper.generateRtoZatcaCreditMemo(memoZatca, invoiceZatca, orderZatca, splitSalesOrder, zatcaConfig, store, taxFactor, memoItems, totalTaxableAmount);
		}

		if (null != zatcaInvoice && zatcaInvoice.getEInvoice().getInvoiceLine().size() > 0) {
			try {
				String URL = zatcaConfig.getBaseUrl() + "/v2/einvoices/generate/async";

				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
				requestHeaders.add("x-cleartax-auth-token", zatcaConfig.getClearTaxToken());
				requestHeaders.add("vat", zatcaConfig.getVatNo());

				HttpEntity<ZatcaInvoice> requestBody = new HttpEntity<>(zatcaInvoice, requestHeaders);
				LOGGER.info("Zatca Body : " + entityId + " : " + mapper.writeValueAsString(zatcaInvoice));
				ResponseEntity<ZatcaInvoiceResponse> response = restTemplate.exchange(URL, HttpMethod.POST, requestBody,
						ZatcaInvoiceResponse.class);

				if (response.getStatusCode() == HttpStatus.OK) {
					LOGGER.info("Zatca getStatus" + mapper.writeValueAsString(response.getBody().getStatus()));
					LOGGER.info("Zatca Error: " + entityId + " :: " + memoZatca.getIncrementId()
							+ ",  Error"
							+ mapper.writeValueAsString(response.getBody().getErrorList()));
					LOGGER.info("Zatca Warning: " + memoZatca.getIncrementId() + ",  Warning"
							+ mapper.writeValueAsString(response.getBody().getWarningList()));
					memoZatca.setZatcaQRCode(response.getBody().getQrCode());
					memoZatca.setZatcaStatus(response.getBody().getInvoiceStatus());
					salesCreditmemoRepository.saveAndFlush(memoZatca);
				}
			} catch (RestClientException | JsonProcessingException e) {
				memoZatca.setZatcaStatus("FAILED");
				salesCreditmemoRepository.saveAndFlush(memoZatca);
				LOGGER.error("exception occoured during Zatca:" + e.getMessage());
			}
		}

	}

	public ZatcaInvoice generateZatcaInvoice(SalesInvoice invoice, SalesOrder order, ZatcaConfig zatcaConfig, Boolean CodRtoZatca ) {
		ZatcaInvoice zatcaInvoice = new ZatcaInvoice();

		Stores store = getStoreById(order.getStoreId());

		String currency = store.getStoreCurrency();

		zatcaInvoice.setDeviceId(zatcaConfig.getDeviceId());

		ZatcaInvoice.EInvoice eInvoice = new ZatcaInvoice.EInvoice();
		eInvoice.setProfileID("reporting:1.0");

		ZatcaInvoice.EInvoice.ID ID = new ZatcaInvoice.EInvoice.ID();
		if(CodRtoZatca){
			ID.setEn("RTO-COD-"+order.getEntityId());
			ID.setAr("RTO-COD-"+order.getEntityId());
		}else{
			ID.setEn(invoice.getIncrementId());
			ID.setAr(invoice.getIncrementId());
		}

		eInvoice.setID(ID);

		ZatcaInvoice.EInvoice.InvoiceTypeCode InvoiceTypeCode = new ZatcaInvoice.EInvoice.InvoiceTypeCode();
		if(CodRtoZatca){
			InvoiceTypeCode.setName(zatcaConfig.getCreditTypeCode().getName());
			InvoiceTypeCode.setValue(zatcaConfig.getCreditTypeCode().getValue());
		}else {
			InvoiceTypeCode.setName(zatcaConfig.getInvoiceTypeCode().getName());
			InvoiceTypeCode.setValue(zatcaConfig.getInvoiceTypeCode().getValue());
		}

		eInvoice.setInvoiceTypeCode(InvoiceTypeCode);

		eInvoice.setIssueDate(convertTimeZone(invoice.getCreatedAt(), 1, DATE_FORMAT));
		eInvoice.setIssueTime(convertTimeZone(invoice.getCreatedAt(), 1, TIME_FORMAT));

		if(CodRtoZatca){
			ZatcaInvoice.EInvoice.BillingReference BillingReference = new ZatcaInvoice.EInvoice.BillingReference();
			ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference InvoiceDocumentReference = new ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference();
			ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference.ID InvoiceDocumentReferenceID = new ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference.ID();
			InvoiceDocumentReferenceID.setAr(invoice.getIncrementId());
			InvoiceDocumentReferenceID.setEn(invoice.getIncrementId());
			InvoiceDocumentReference.setID(InvoiceDocumentReferenceID);
			BillingReference.setInvoiceDocumentReference(InvoiceDocumentReference);

			List<ZatcaInvoice.EInvoice.BillingReference> BillingReferenceList = new ArrayList<>();
			BillingReferenceList.add(BillingReference);

			eInvoice.setBillingReference(BillingReferenceList);

			ZatcaInvoice.EInvoice.PaymentMeans PaymentMeans = new ZatcaInvoice.EInvoice.PaymentMeans();

			ZatcaInvoice.EInvoice.InstructionNote InstructionNote = new ZatcaInvoice.EInvoice.InstructionNote();
			InstructionNote.setAr("RTO");
			InstructionNote.setEn("RTO");

			PaymentMeans.setInstructionNote(InstructionNote);
			PaymentMeans.setPaymentMeansCode("1");

			List<ZatcaInvoice.EInvoice.PaymentMeans> PaymentMeansList = new ArrayList<>();
			PaymentMeansList.add(PaymentMeans);

			eInvoice.setPaymentMeans(PaymentMeansList);
			eInvoice.setIssueDate(convertTimeZone(new Timestamp(new Date().getTime()), 1, DATE_FORMAT));
			eInvoice.setIssueTime(convertTimeZone(new Timestamp(new Date().getTime()), 1, TIME_FORMAT));
		}

		eInvoice.setTaxCurrencyCode("SAR");
		eInvoice.setDocumentCurrencyCode(currency);
		eInvoice.setAccountingSupplierParty(zatcaConfig.getAccountingSupplierParty());

		eInvoice.setAccountingCustomerParty(getCustomerAddress(order));

		List<InvoiceLine> invoiceLines = new ArrayList<>();
		BigDecimal totalPriceInclTax = BigDecimal.ZERO;
		BigDecimal totalPriceExclTax = BigDecimal.ZERO;
		BigDecimal totalGiftVoucherAmount = BigDecimal.ZERO;

		Map<String, ProductResponseBody> productsFromMulin = mulinHelper
				.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);

		List<SalesOrderItem> salesOrderItems = order.getSalesOrderItem().stream()
				.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());

		List<Integer> salesOrderItemIds = salesOrderItems.stream().map(SalesOrderItem::getItemId)
				.collect(Collectors.toList());

		List<SalesInvoiceItem> salesInvoiceItems = invoice.getSalesInvoiceItem().stream()
				.filter(obj -> salesOrderItemIds.contains(obj.getOrderItemId())).collect(Collectors.toList());
		Integer lineCount = 1;
		for (SalesInvoiceItem invoiceItem : salesInvoiceItems) {

			SalesOrderItem orderItem = salesOrderItems.stream()
					.filter(e -> e.getItemId().equals(invoiceItem.getOrderItemId())).findAny().orElse(null);

			// start
			BigDecimal taxFactor = orderHelper.getExclTaxfactor(orderItem.getTaxPercent());

			BigDecimal giftVoucherAmount = BigDecimal.ZERO;
			if (null != orderItem.getSubSalesOrderItem()) {
				SubSalesOrderItem subSalesOrderItem = orderItem.getSubSalesOrderItem().stream()
						.filter(e -> e.isGiftVoucher()).findFirst().orElse(null);
				if (null != subSalesOrderItem) {
					giftVoucherAmount = subSalesOrderItem.getDiscount();
				}
			}
			totalGiftVoucherAmount = totalGiftVoucherAmount.add(giftVoucherAmount);
			BigDecimal unitPriceExclTax = orderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP); // Unit
			// price

			BigDecimal productOriginalPriceExclDiscount = unitPriceExclTax.multiply(invoiceItem.getQuantity()); // original
			// price

			BigDecimal discountProductLevelExclTax = orderItem.getOriginalPrice()
					.subtract(invoiceItem.getPriceInclTax());
			discountProductLevelExclTax = discountProductLevelExclTax.multiply(invoiceItem.getQuantity());
			if (discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
				discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
			}

			BigDecimal discountCouponExclTaxProduct = invoiceItem.getDiscountAmount().subtract(giftVoucherAmount);
			discountCouponExclTaxProduct = discountCouponExclTaxProduct.divide(taxFactor, 2, RoundingMode.HALF_UP);

			BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct); // original
			// discount

			BigDecimal taxablePriceProduct = productOriginalPriceExclDiscount.subtract(discountExclTaxProduct)
					.setScale(2, RoundingMode.HALF_UP); // taxable price price Excl. tax
			if (taxablePriceProduct.compareTo(BigDecimal.ZERO) < 0) {
				taxablePriceProduct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
			}
			BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP);
			totalTaxAmountProduct = totalTaxAmountProduct.multiply(orderItem.getTaxPercent()).setScale(2,
					RoundingMode.HALF_UP); // tax

			BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2,
					RoundingMode.HALF_UP); // price Incl. tax
			// end

			////
			InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();

			ItemName.setEn(invoiceItem.getName());
			ItemName.setAr(invoiceItem.getName());
			if (productsFromMulin.containsKey(orderItem.getParentSku())) {
				ProductResponseBody productDetailsMulin = productsFromMulin.get(orderItem.getParentSku());
				if (null != productDetailsMulin.getAttributes().getName().getArabic()) {
					ItemName.setAr(productDetailsMulin.getAttributes().getName().getArabic());
					ItemName.setEn(productDetailsMulin.getAttributes().getName().getEnglish());
				}
			}

			InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
			ClassifiedTaxCategory.setID(this.getClassifiedTaxCategory(orderItem.getTaxPercent()));
			ClassifiedTaxCategory.setPercent(orderItem.getTaxPercent().setScale(2, RoundingMode.HALF_UP).toString());

			InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme TaxScheme = new InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme();
			TaxScheme.setID("VAT");
			ClassifiedTaxCategory.setTaxScheme(TaxScheme);

			InvoiceLine invoiceLine = new InvoiceLine();
			invoiceLine.setID(lineCount.toString());

			InvoiceLine.InvoicedQuantity InvoicedQuantity = new InvoiceLine.InvoicedQuantity();
			InvoicedQuantity.setValue(invoiceItem.getQuantity().setScale(2, RoundingMode.HALF_UP).toString());
			invoiceLine.setInvoicedQuantity(InvoicedQuantity);

			InvoiceLine.Item Item = new InvoiceLine.Item();
			Item.setName(ItemName);

			InvoiceLine.Item.SellersItemIdentification sellersItemIdentification = new InvoiceLine.Item.SellersItemIdentification();
			//
			InvoiceLine.Item.SellersItemIdentification.ID sellersItemIdentificationID = new InvoiceLine.Item.SellersItemIdentification.ID();
			sellersItemIdentificationID.setAr(invoiceItem.getSku());
			sellersItemIdentificationID.setEn(invoiceItem.getSku());
			sellersItemIdentification.setID(sellersItemIdentificationID);

			Item.setSellersItemIdentification(sellersItemIdentification);
			Item.setClassifiedTaxCategory(ClassifiedTaxCategory);

			// Unit Price before discount
			InvoiceLine.Price Price = new InvoiceLine.Price();
			InvoiceLine.PriceAmount PriceAmount = new InvoiceLine.PriceAmount();
			PriceAmount.setCurrencyID(currency);
			PriceAmount.setValue(parseNullStr(unitPriceExclTax));// unit price Exclusive Tax
			Price.setPriceAmount(PriceAmount);
			invoiceLine.setPrice(Price);

			// Discount
			List<InvoiceLine.AllowanceCharge> AllowanceCharges = new ArrayList<>();
			InvoiceLine.AllowanceCharge AllowanceCharge = new InvoiceLine.AllowanceCharge();

			InvoiceLine.Amount AllowanceAmount = new InvoiceLine.Amount();
			AllowanceAmount.setCurrencyID(currency);
			AllowanceAmount.setValue(parseNullStr(discountExclTaxProduct.setScale(2, RoundingMode.HALF_UP)));
			AllowanceCharge.setAmount(AllowanceAmount);

			InvoiceLine.AllowanceChargeReason AllowanceChargeReason = new InvoiceLine.AllowanceChargeReason();
			AllowanceChargeReason.setAr(zatcaConfig.getDiscountText().getAr());
			AllowanceChargeReason.setEn(zatcaConfig.getDiscountText().getEn());
			AllowanceCharge.setAllowanceChargeReason(AllowanceChargeReason);

			AllowanceCharge.setChargeIndicator(false);
			AllowanceCharges.add(AllowanceCharge);

			invoiceLine.setAllowanceCharge(AllowanceCharges);

			// taxable price after discount
			InvoiceLine.LineExtensionAmount LineExtensionAmount = new InvoiceLine.LineExtensionAmount();
			LineExtensionAmount.setCurrencyID(currency);
			LineExtensionAmount.setValue(parseNullStr(taxablePriceProduct));
			invoiceLine.setLineExtensionAmount(LineExtensionAmount);

			// tax amount
			InvoiceLine.TaxTotal TaxTotal = new InvoiceLine.TaxTotal();
			InvoiceLine.TaxTotal.TaxAmount TaxAmount = new InvoiceLine.TaxTotal.TaxAmount();
			TaxAmount.setCurrencyID(currency);
			TaxAmount.setValue(parseNullStr(totalTaxAmountProduct));
			TaxTotal.setTaxAmount(TaxAmount);

			InvoiceLine.TaxTotal.RoundingAmount RoundingAmount = new InvoiceLine.TaxTotal.RoundingAmount();
			RoundingAmount.setCurrencyID(currency);
			RoundingAmount.setValue(parseNullStr(totalPriceInclTaxProduct));// Inclusive Tax
			TaxTotal.setRoundingAmount(RoundingAmount);
			invoiceLine.setTaxTotal(TaxTotal);

			totalPriceExclTax = totalPriceExclTax.add(taxablePriceProduct);
			totalPriceInclTax = totalPriceInclTax.add(totalPriceInclTaxProduct);

			///
			List<InvoiceLine.Item.AdditionalItemProperty> AdditionalItemProperty = new ArrayList<>();
			InvoiceLine.Item.AdditionalItemProperty ItemSubtotal = new InvoiceLine.Item.AdditionalItemProperty();
			ItemSubtotal.setName("product_subtotal");
			ItemSubtotal.setValue(parseNullStr(
					unitPriceExclTax.multiply(invoiceItem.getQuantity()).setScale(2, RoundingMode.HALF_UP)));
			AdditionalItemProperty.add(ItemSubtotal);

			Item.setAdditionalItemProperty(AdditionalItemProperty);

			invoiceLine.setItem(Item);

			lineCount++;
			invoiceLines.add(invoiceLine);

		}

		BigDecimal bigdecimal_100 = new BigDecimal("100");
		BigDecimal taxFactor = orderHelper.getExclTaxfactor(store.getTaxPercentage());
		// Shipping fee as line Item
		if (null != invoice.getShippingAmount() && invoice.getShippingAmount().compareTo(BigDecimal.ZERO) > 0) {
			InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();
			ItemName.setAr(zatcaConfig.getShippingFeeText().getAr());
			ItemName.setEn(zatcaConfig.getShippingFeeText().getEn());

			InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
			ClassifiedTaxCategory.setID(this.getClassifiedTaxCategory(store.getTaxPercentage()));
			ClassifiedTaxCategory.setPercent(parseNullStr(store.getTaxPercentage().setScale(2, RoundingMode.HALF_UP)));

			BigDecimal priceInclTax = invoice.getShippingAmount();
			BigDecimal priceExclTax = priceInclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
			BigDecimal taxAmount = priceInclTax.subtract(priceExclTax);

			String linePriceInclTax = parseNullStr(priceInclTax.setScale(2, RoundingMode.HALF_UP));
			String lineTaxAmount = parseNullStr(taxAmount.setScale(2, RoundingMode.HALF_UP));
			String linePriceExclTax = parseNullStr(priceExclTax.setScale(2, RoundingMode.HALF_UP));

			InvoiceLine invoiceLine = getInvoiceLine(lineCount, "1", ItemName, ClassifiedTaxCategory, currency,
					lineTaxAmount, linePriceInclTax, linePriceExclTax, "");

			invoiceLines.add(invoiceLine);

			totalPriceInclTax = totalPriceInclTax.add(priceInclTax);
			totalPriceExclTax = totalPriceExclTax.add(priceExclTax);
			lineCount++;
		}

		// COD Fee as line Item
		if (null != invoice.getCashOnDeliveryFee() && invoice.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
			InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();
			ItemName.setAr(zatcaConfig.getCODFeeText().getAr());
			ItemName.setEn(zatcaConfig.getCODFeeText().getEn());

			InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
			ClassifiedTaxCategory.setID(this.getClassifiedTaxCategory(store.getTaxPercentage()));
			ClassifiedTaxCategory.setPercent(parseNullStr(store.getTaxPercentage().setScale(2, RoundingMode.HALF_UP)));

			BigDecimal priceInclTax = invoice.getCashOnDeliveryFee();
			BigDecimal priceExclTax = priceInclTax.divide(taxFactor, 2, RoundingMode.HALF_UP);
			BigDecimal taxAmount = priceInclTax.subtract(priceExclTax);

			String linePriceInclTax = parseNullStr(priceInclTax.setScale(2, RoundingMode.HALF_UP));
			String lineTaxAmount = parseNullStr(taxAmount.setScale(2, RoundingMode.HALF_UP));
			String linePriceExclTax = parseNullStr(priceExclTax.setScale(2, RoundingMode.HALF_UP));

			InvoiceLine invoiceLine = getInvoiceLine(lineCount, "1", ItemName, ClassifiedTaxCategory, currency,
					lineTaxAmount, linePriceInclTax, linePriceExclTax, "");

			invoiceLines.add(invoiceLine);

			totalPriceInclTax = totalPriceInclTax.add(priceInclTax);
			totalPriceExclTax = totalPriceExclTax.add(priceExclTax);
			lineCount++;
		}

		// Donation as line Item
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()
				&& order.getSubSalesOrder().getDonationAmount().compareTo(BigDecimal.ZERO) > 0) {
			InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();
			ItemName.setAr(zatcaConfig.getDonationFeeText().getAr());
			ItemName.setEn(zatcaConfig.getDonationFeeText().getEn());

			InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
			ClassifiedTaxCategory.setID("O");
			ClassifiedTaxCategory.setPercent("0.00");

			String linePrice = parseNullStr(
					order.getSubSalesOrder().getDonationAmount().setScale(2, RoundingMode.HALF_UP));

			InvoiceLine invoiceLine = getInvoiceLine(lineCount, "1", ItemName, ClassifiedTaxCategory, currency, "0.00",
					linePrice, linePrice, "");

			invoiceLines.add(invoiceLine);

			totalPriceInclTax = totalPriceInclTax.add(order.getSubSalesOrder().getDonationAmount());
			totalPriceExclTax = totalPriceExclTax.add(order.getSubSalesOrder().getDonationAmount());
			lineCount++;
		}

		// Import Fee as line Item
		if (null != order.getImportFee() && order.getImportFee().compareTo(BigDecimal.ZERO) == 1) {
			InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();
			ItemName.setAr(zatcaConfig.getImportFeeText().getAr());
			ItemName.setEn(zatcaConfig.getImportFeeText().getEn());

			InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
			ClassifiedTaxCategory.setID("Z");
			ClassifiedTaxCategory.setPercent("0.00");

			String linePrice = parseNullStr(order.getImportFee().setScale(2, RoundingMode.HALF_UP));

			InvoiceLine invoiceLine = getInvoiceLine(lineCount, "1", ItemName, ClassifiedTaxCategory, currency, "0.00",
					linePrice, linePrice, "");

			invoiceLines.add(invoiceLine);

			totalPriceInclTax = totalPriceInclTax.add(order.getImportFee());
			totalPriceExclTax = totalPriceExclTax.add(order.getImportFee());
			lineCount++;
		}

		eInvoice.setInvoiceLine(invoiceLines);

		totalPriceExclTax = totalPriceExclTax.setScale(2, RoundingMode.HALF_UP);

		ZatcaInvoice.EInvoice.LegalMonetaryTotal LegalMonetaryTotal = new ZatcaInvoice.EInvoice.LegalMonetaryTotal();

		ZatcaInvoice.EInvoice.LineExtensionAmount LineExtensionAmount = new ZatcaInvoice.EInvoice.LineExtensionAmount();
		LineExtensionAmount.setCurrencyID(currency);
		LineExtensionAmount.setValue(parseNullStr(totalPriceExclTax));
		LegalMonetaryTotal.setLineExtensionAmount(LineExtensionAmount);

		ZatcaInvoice.EInvoice.TaxExclusiveAmount TaxExclusiveAmount = new ZatcaInvoice.EInvoice.TaxExclusiveAmount();
		TaxExclusiveAmount.setCurrencyID(currency);
		TaxExclusiveAmount.setValue(parseNullStr(totalPriceExclTax));
		LegalMonetaryTotal.setTaxExclusiveAmount(TaxExclusiveAmount);

		ZatcaInvoice.EInvoice.TaxAmount taxAmountS = new ZatcaInvoice.EInvoice.TaxAmount();

		setTaxTotalObject(invoiceLines, eInvoice, currency, store, taxAmountS);

		BigDecimal taxInclusiveAmountValue = totalPriceExclTax.add(new BigDecimal(taxAmountS.getValue()));

		ZatcaInvoice.EInvoice.TaxInclusiveAmount TaxInclusiveAmount = new ZatcaInvoice.EInvoice.TaxInclusiveAmount();
		TaxInclusiveAmount.setCurrencyID(currency);
		TaxInclusiveAmount.setValue(parseNullStr(taxInclusiveAmountValue));
		LegalMonetaryTotal.setTaxInclusiveAmount(TaxInclusiveAmount);

		BigDecimal netPayableAmount = invoice.getGrandTotal();

		LinkedHashMap<String, String> customFields = new LinkedHashMap<>();
		if (null != invoice.getEasValueInCurrency() && invoice.getEasValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
			customFields.put("Styli Cash",
					parseNullStr(invoice.getEasValueInCurrency().setScale(2, RoundingMode.HALF_UP)));
			netPayableAmount = netPayableAmount.add(invoice.getEasValueInCurrency());
		}

		if (null != invoice.getAmstorecreditAmount()
				&& invoice.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) > 0) {
			customFields.put("Styli Credits",
					parseNullStr(invoice.getAmstorecreditAmount().setScale(2, RoundingMode.HALF_UP)));
			netPayableAmount = netPayableAmount.add(invoice.getAmstorecreditAmount());
		}

		if (null != invoice.getShukranBurnedPoints()
				&& invoice.getShukranBurnedPoints().compareTo(BigDecimal.ZERO) > 0) {
			customFields.put("Shukran Points",
					parseNullStr(invoice.getShukranBurnedValueInCurrency().setScale(2, RoundingMode.HALF_UP)));

		}
		// netPayableAmount = netPayableAmount.subtract(styliCoinSpend);
		if (totalGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
			customFields.put("Gift Voucher", parseNullStr(totalGiftVoucherAmount.setScale(2, RoundingMode.HALF_UP)));
			netPayableAmount = netPayableAmount.add(totalGiftVoucherAmount);
		}

		BigDecimal payableAmountToSet= invoice.getGrandTotal();
		if(order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency()!= null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO)>0){
			payableAmountToSet = payableAmountToSet.subtract(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
		}
		if(payableAmountToSet.compareTo(BigDecimal.ZERO)>0) {
			customFields.put(PAYABLE_AMOUNT, parseNullStr(payableAmountToSet.setScale(2, RoundingMode.HALF_UP)));
		}

		BigDecimal roundingAmount = netPayableAmount.subtract(taxInclusiveAmountValue);
		if (roundingAmount.compareTo(BigDecimal.ZERO) != 0) {
			customFields.put("Rounding Payable Amount", parseNullStr(roundingAmount.setScale(2, RoundingMode.HALF_UP)));

			ZatcaInvoice.EInvoice.PayableAmount PayableRoundingAmount = new ZatcaInvoice.EInvoice.PayableAmount();
			PayableRoundingAmount.setCurrencyID(currency);
			PayableRoundingAmount.setValue(parseNullStr(roundingAmount.setScale(2, RoundingMode.HALF_UP)));
			LegalMonetaryTotal.setPaybleRoundingAmount(PayableRoundingAmount);
		}

		ZatcaInvoice.EInvoice.PayableAmount PayableAmount = new ZatcaInvoice.EInvoice.PayableAmount();
		PayableAmount.setCurrencyID(currency);
		PayableAmount.setValue(parseNullStr(netPayableAmount.setScale(2, RoundingMode.HALF_UP)));
		LegalMonetaryTotal.setPayableAmount(PayableAmount);

		String[] KSAStoreIds = { "1", "3" };
		if (!Arrays.stream(KSAStoreIds).anyMatch(str -> str.equals(store.getStoreId()))) {
			customFields.put("custom_header_en", zatcaConfig.getCustomHeaderTextInvoice().getEn());
			customFields.put("custom_header_ar", zatcaConfig.getCustomHeaderTextInvoice().getAr());
		}

		// Log the net payable amount and store to base rate for debugging
		LOGGER.info("Net Payable Amount: " + netPayableAmount);
		LOGGER.info("Store to Base Rate: " + order.getStoreToBaseRate());

		BigDecimal basePayableAmount = omsorderentityConverter.getBaseValueDecimal(netPayableAmount,
				order.getStoreToBaseRate());

		// Round the base payable amount to 2 decimal places
		BigDecimal payableAmount = basePayableAmount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();

		LOGGER.info("Base Payable Amount in Zatca Invoice: " + payableAmount);

		if (basePayableAmount != null) {
			customFields.put("Base Payable Amount", payableAmount.toString());
		}

		zatcaInvoice.setCustomFields(customFields);

		eInvoice.setLegalMonetaryTotal(LegalMonetaryTotal);

		zatcaInvoice.setEInvoice(eInvoice);

		return zatcaInvoice;

	}

	public ZatcaInvoice generateZatcaCreditMemo(SalesCreditmemo memo, SalesInvoice invoice, SalesOrder order,
	SplitSalesOrder splitSalesOrder,
	ZatcaConfig zatcaConfig) {
		Integer storeId = order != null ? order.getStoreId() : splitSalesOrder.getStoreId();


		Stores store = getStoreById(storeId);
		String currency = store.getStoreCurrency();

		ZatcaInvoice zatcaInvoice = new ZatcaInvoice();

		zatcaInvoice.setDeviceId(zatcaConfig.getDeviceId());

		ZatcaInvoice.EInvoice eInvoice = new ZatcaInvoice.EInvoice();
		eInvoice.setProfileID("reporting:1.0");

		ZatcaInvoice.EInvoice.ID ID = new ZatcaInvoice.EInvoice.ID();
		ID.setEn(memo.getIncrementId());
		ID.setAr(memo.getIncrementId());
		eInvoice.setID(ID);

		ZatcaInvoice.EInvoice.InvoiceTypeCode InvoiceTypeCode = new ZatcaInvoice.EInvoice.InvoiceTypeCode();
		InvoiceTypeCode.setName(zatcaConfig.getCreditTypeCode().getName());
		InvoiceTypeCode.setValue(zatcaConfig.getCreditTypeCode().getValue());
		eInvoice.setInvoiceTypeCode(InvoiceTypeCode);

		eInvoice.setIssueDate(convertTimeZone(memo.getCreatedAt(), 1, DATE_FORMAT));
		eInvoice.setIssueTime(convertTimeZone(memo.getCreatedAt(), 1, TIME_FORMAT));

		ZatcaInvoice.EInvoice.BillingReference BillingReference = new ZatcaInvoice.EInvoice.BillingReference();
		ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference InvoiceDocumentReference = new ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference();
		ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference.ID InvoiceDocumentReferenceID = new ZatcaInvoice.EInvoice.BillingReference.InvoiceDocumentReference.ID();
		InvoiceDocumentReferenceID.setAr(invoice.getIncrementId());
		InvoiceDocumentReferenceID.setEn(invoice.getIncrementId());
		InvoiceDocumentReference.setID(InvoiceDocumentReferenceID);
		BillingReference.setInvoiceDocumentReference(InvoiceDocumentReference);

		List<ZatcaInvoice.EInvoice.BillingReference> BillingReferenceList = new ArrayList<>();
		BillingReferenceList.add(BillingReference);

		eInvoice.setBillingReference(BillingReferenceList);

		eInvoice.setTaxCurrencyCode("SAR");
		eInvoice.setDocumentCurrencyCode(currency);
		eInvoice.setAccountingSupplierParty(zatcaConfig.getAccountingSupplierParty());

		ZatcaInvoice.EInvoice.AccountingCustomerParty AccountingCustomerParty = new ZatcaInvoice.EInvoice.AccountingCustomerParty();
		LinkedHashMap<String, String> party = new LinkedHashMap<>();
		AccountingCustomerParty.setParty(party);

		eInvoice.setAccountingCustomerParty(getCustomerAddress(order != null ? order : splitSalesOrder.getSalesOrder()));

		List<InvoiceLine> invoiceLines = new ArrayList<>();
		BigDecimal totalPriceInclTax = BigDecimal.ZERO;
		BigDecimal totalPriceExclTax = BigDecimal.ZERO;

		BigDecimal storeToBaseRate = order != null ? order.getStoreToBaseRate() : splitSalesOrder.getStoreToBaseRate();

		Integer lineCount = 1;

		List<SalesOrderItem> salesOrderItems = new ArrayList<>();
		List<SplitSalesOrderItem> splitSalesOrderItems = new ArrayList<>();
		if(order != null){
			salesOrderItems = salesOrderItemRepository
			.findSalesOrderItemConfigurableByOrderId(memo.getOrderId());
		}else{
			splitSalesOrderItems = splitSalesOrderItemRepository
			.findSalesOrderItemConfigurableByOrderId(memo.getSplitOrderId());
		}

		List<Integer> saleOrderItemIds = (order != null ? salesOrderItems.stream().map(SalesOrderItem::getItemId)
		: splitSalesOrderItems.stream().map(SplitSalesOrderItem::getItemId))
		.collect(Collectors.toList());

		List<SalesCreditmemoItem> salesCreditMemoItems = salesCreditmemoItemRepository
		.findByParentId(memo.getEntityId()).stream()
		.filter(obj -> saleOrderItemIds.contains(obj.getOrderItemId())).collect(Collectors.toList());

		Map<String, ProductResponseBody> productsFromMulin = order != null ? mulinHelper
		.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate)
		: mulinHelper.getMulinProductsFromSplitOrder(Collections.singletonList(splitSalesOrder), restTemplate);

		for (SalesCreditmemoItem creditMemoItem : salesCreditMemoItems) {

		SalesOrderItem orderItem = salesOrderItems.stream()
		.filter(e -> e.getItemId().equals(creditMemoItem.getOrderItemId())).findAny().orElse(null);

		SplitSalesOrderItem splitOrderItem = splitSalesOrderItems.stream()
		.filter(e -> e.getItemId().equals(creditMemoItem.getOrderItemId())).findAny().orElse(null);

		if (null != orderItem || null != splitOrderItem) {
		BigDecimal taxPercent = orderItem != null ? orderItem.getTaxPercent() : splitOrderItem.getTaxPercent();
		String parentSku = orderItem != null ? orderItem.getParentSku() : splitOrderItem.getParentSku();
		BigDecimal originalPrice = orderItem != null ? orderItem.getOriginalPrice() : splitOrderItem.getOriginalPrice();

		BigDecimal taxFactor = orderHelper.getExclTaxfactor(taxPercent);
		InvoiceLine invoiceLine = new InvoiceLine();
		invoiceLine.setID(lineCount.toString());

		InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();

		ItemName.setEn(creditMemoItem.getName());
		ItemName.setAr(creditMemoItem.getName());
		if (productsFromMulin.containsKey(parentSku)) {
		ProductResponseBody productDetailsMulin = productsFromMulin.get(parentSku);
		if (null != productDetailsMulin.getAttributes().getName().getArabic()) {
		ItemName.setAr(productDetailsMulin.getAttributes().getName().getArabic());
		ItemName.setEn(productDetailsMulin.getAttributes().getName().getEnglish());
		}
		}
		InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
		ClassifiedTaxCategory.setID(this.getClassifiedTaxCategory(taxPercent));
		ClassifiedTaxCategory
		.setPercent(taxPercent.setScale(2, RoundingMode.HALF_UP).toString());

		BigDecimal unitPriceExclTax = originalPrice.divide(taxFactor, 2, RoundingMode.HALF_UP); // Unit
		// price

		BigDecimal productOriginalPriceExclDiscount = unitPriceExclTax.multiply(creditMemoItem.getQty()); // original
		// price

		BigDecimal discountProductLevelExclTax = originalPrice
		.subtract(creditMemoItem.getPriceInclTax());
		discountProductLevelExclTax = discountProductLevelExclTax.multiply(creditMemoItem.getQty());
		if (discountProductLevelExclTax.compareTo(BigDecimal.ZERO) != 0) {
		discountProductLevelExclTax = discountProductLevelExclTax.divide(taxFactor, 2,
		RoundingMode.HALF_UP);
		}
		if (creditMemoItem.getVoucherAmount() == null) {
		BigDecimal vc = BigDecimal.ZERO;
		creditMemoItem.setVoucherAmount(vc);
		}
		BigDecimal discountCouponExclTaxProduct = creditMemoItem.getDiscountAmount()
		.subtract(creditMemoItem.getVoucherAmount());
		discountCouponExclTaxProduct = discountCouponExclTaxProduct.divide(taxFactor, 2, RoundingMode.HALF_UP);

		BigDecimal discountExclTaxProduct = discountProductLevelExclTax.add(discountCouponExclTaxProduct); // original
		// discount

		BigDecimal taxablePriceProduct = productOriginalPriceExclDiscount.subtract(discountExclTaxProduct)
		.setScale(2, RoundingMode.HALF_UP); // taxable price price Excl. tax
		BigDecimal totalTaxAmountProduct = taxablePriceProduct.divide(new BigDecimal(100), 4,
		RoundingMode.HALF_UP);
		totalTaxAmountProduct = totalTaxAmountProduct.multiply(taxPercent).setScale(2,
		RoundingMode.HALF_UP); // tax

		BigDecimal totalPriceInclTaxProduct = taxablePriceProduct.add(totalTaxAmountProduct).setScale(2,
		RoundingMode.HALF_UP); // price Incl. tax

		InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme TaxScheme = new InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme();
		TaxScheme.setID("VAT");
		ClassifiedTaxCategory.setTaxScheme(TaxScheme);

		InvoiceLine.InvoicedQuantity InvoicedQuantity = new InvoiceLine.InvoicedQuantity();
		InvoicedQuantity.setValue(creditMemoItem.getQty().setScale(2, RoundingMode.HALF_UP).toString());
		invoiceLine.setInvoicedQuantity(InvoicedQuantity);

		InvoiceLine.Item Item = new InvoiceLine.Item();
		Item.setName(ItemName);

		InvoiceLine.Item.SellersItemIdentification sellersItemIdentification = new InvoiceLine.Item.SellersItemIdentification();
		//
		InvoiceLine.Item.SellersItemIdentification.ID sellersItemIdentificationID = new InvoiceLine.Item.SellersItemIdentification.ID();
		sellersItemIdentificationID.setAr(creditMemoItem.getSku());
		sellersItemIdentificationID.setEn(creditMemoItem.getSku());
		sellersItemIdentification.setID(sellersItemIdentificationID);

		Item.setSellersItemIdentification(sellersItemIdentification);
		Item.setClassifiedTaxCategory(ClassifiedTaxCategory);

		// Unit Price before discount
		InvoiceLine.Price Price = new InvoiceLine.Price();
		InvoiceLine.PriceAmount PriceAmount = new InvoiceLine.PriceAmount();
		PriceAmount.setCurrencyID(currency);
		PriceAmount.setValue(parseNullStr(unitPriceExclTax));// unit price Exclusive Tax
		Price.setPriceAmount(PriceAmount);
		invoiceLine.setPrice(Price);

		// Discount
		List<InvoiceLine.AllowanceCharge> AllowanceCharges = new ArrayList<>();
		InvoiceLine.AllowanceCharge AllowanceCharge = new InvoiceLine.AllowanceCharge();

		InvoiceLine.Amount AllowanceAmount = new InvoiceLine.Amount();
		AllowanceAmount.setCurrencyID(currency);
		AllowanceAmount.setValue(parseNullStr(discountExclTaxProduct.setScale(2, RoundingMode.HALF_UP)));
		AllowanceCharge.setAmount(AllowanceAmount);

		InvoiceLine.AllowanceChargeReason AllowanceChargeReason = new InvoiceLine.AllowanceChargeReason();
		AllowanceChargeReason.setAr(zatcaConfig.getDiscountText().getAr());
		AllowanceChargeReason.setEn(zatcaConfig.getDiscountText().getEn());
		AllowanceCharge.setAllowanceChargeReason(AllowanceChargeReason);

		// InvoiceLine.BaseAmount BaseAmount = new InvoiceLine.BaseAmount();
		// BaseAmount.setCurrencyID(currency);
		// BaseAmount.setValue(parseNullStr(unitPriceExclTax.multiply(creditMemoItem.getQty()).setScale(2,
		// RoundingMode.HALF_UP)));
		// AllowanceCharge.setBaseAmount(BaseAmount);

		AllowanceCharge.setChargeIndicator(false);
		AllowanceCharges.add(AllowanceCharge);
		invoiceLine.setAllowanceCharge(AllowanceCharges);

		// taxable price after discount
		InvoiceLine.LineExtensionAmount LineExtensionAmount = new InvoiceLine.LineExtensionAmount();
		LineExtensionAmount.setCurrencyID(currency);
		LineExtensionAmount.setValue(parseNullStr(taxablePriceProduct));
		invoiceLine.setLineExtensionAmount(LineExtensionAmount);

		// tax amount
		InvoiceLine.TaxTotal TaxTotal = new InvoiceLine.TaxTotal();
		InvoiceLine.TaxTotal.TaxAmount TaxAmount = new InvoiceLine.TaxTotal.TaxAmount();
		TaxAmount.setCurrencyID(currency);
		TaxAmount.setValue(parseNullStr(totalTaxAmountProduct));
		TaxTotal.setTaxAmount(TaxAmount);

		InvoiceLine.TaxTotal.RoundingAmount RoundingAmount = new InvoiceLine.TaxTotal.RoundingAmount();
		RoundingAmount.setCurrencyID(currency);
		RoundingAmount.setValue(parseNullStr(totalPriceInclTaxProduct));// Inclusive Tax
		TaxTotal.setRoundingAmount(RoundingAmount);
		invoiceLine.setTaxTotal(TaxTotal);

		totalPriceExclTax = totalPriceExclTax.add(taxablePriceProduct);
		totalPriceInclTax = totalPriceInclTax.add(totalPriceInclTaxProduct);

		List<InvoiceLine.Item.AdditionalItemProperty> AdditionalItemProperty = new ArrayList<>();
		InvoiceLine.Item.AdditionalItemProperty ItemSubtotal = new InvoiceLine.Item.AdditionalItemProperty();
		ItemSubtotal.setName("product_subtotal");
		ItemSubtotal.setValue(parseNullStr(
		unitPriceExclTax.multiply(creditMemoItem.getQty()).setScale(2, RoundingMode.HALF_UP)));
		AdditionalItemProperty.add(ItemSubtotal);

		Item.setAdditionalItemProperty(AdditionalItemProperty);

		invoiceLine.setItem(Item);

		lineCount++;

		invoiceLines.add(invoiceLine);

		String status = order != null ? order.getStatus() : splitSalesOrder.getSalesOrder().getStatus();
		double shippingAmount = (invoice.shippingAmount != null) ? invoice.shippingAmount.doubleValue() : 0.0;
		if (shippingAmount != 0 && "rto".equals(status)) {

		InvoiceLine invoiceLine1 = new InvoiceLine();

		InvoiceLine.Item.Name additionalItemName = new InvoiceLine.Item.Name();
		additionalItemName.setEn("Additional charges");
		additionalItemName.setAr("البند أ");

		InvoiceLine.Item additionalItem = new InvoiceLine.Item();
		additionalItem.setName(additionalItemName);

		InvoiceLine.Item.SellersItemIdentification additionalSellersItemIdentification = new InvoiceLine.Item.SellersItemIdentification();
		InvoiceLine.Item.SellersItemIdentification.ID additionalSellersItemIdentificationID = new InvoiceLine.Item.SellersItemIdentification.ID();
		additionalSellersItemIdentificationID.setEn(creditMemoItem.getSku());
		additionalSellersItemIdentificationID.setAr(creditMemoItem.getSku());
		additionalSellersItemIdentification.setID(additionalSellersItemIdentificationID);
		additionalItem.setSellersItemIdentification(additionalSellersItemIdentification);

		InvoiceLine.Item.ClassifiedTaxCategory additionalClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
		additionalClassifiedTaxCategory.setID("S");
		additionalClassifiedTaxCategory
		.setPercent(taxPercent.setScale(2, RoundingMode.HALF_UP).toString());
		InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme additionalTaxScheme = new InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme();
		additionalTaxScheme.setID("VAT");
		additionalClassifiedTaxCategory.setTaxScheme(additionalTaxScheme);
		additionalItem.setClassifiedTaxCategory(additionalClassifiedTaxCategory);

		invoiceLine1.setItem(additionalItem);

		BigDecimal shippingAmountBigDecimal = BigDecimal.valueOf(shippingAmount);
		BigDecimal netShippingAmount = shippingAmountBigDecimal.divide(taxFactor, 2, RoundingMode.HALF_UP);

		InvoiceLine.Price additionalPrice = new InvoiceLine.Price();

		InvoiceLine.PriceAmount additionalPriceAmount = new InvoiceLine.PriceAmount();
		additionalPriceAmount.setCurrencyID(currency);
		additionalPriceAmount.setValue(String.valueOf(netShippingAmount));
		additionalPrice.setPriceAmount(additionalPriceAmount);

		InvoiceLine.Price.PriceAllowanceCharge additionalPriceAllowanceCharge = new InvoiceLine.Price.PriceAllowanceCharge();
		additionalPriceAllowanceCharge.setChargeIndicator(false);

		InvoiceLine.BaseAmount additionalBaseAmount = new InvoiceLine.BaseAmount();
		additionalBaseAmount.setCurrencyID(currency);
		additionalBaseAmount.setValue(String.valueOf(netShippingAmount));
		additionalPriceAllowanceCharge.setBaseAmount(additionalBaseAmount);

		InvoiceLine.Amount additionalAmount = new InvoiceLine.Amount();
		additionalAmount.setCurrencyID(currency);
		additionalAmount.setValue("0");
		additionalPriceAllowanceCharge.setAmount(additionalAmount);

		additionalPrice.setAllowanceCharge(additionalPriceAllowanceCharge);

		invoiceLine1.setPrice(additionalPrice);

		// Set InvoicedQuantity
		InvoiceLine.InvoicedQuantity additionalInvoicedQuantity = new InvoiceLine.InvoicedQuantity();
		additionalInvoicedQuantity.setValue("1");
		additionalInvoicedQuantity.setUnitCode("null");
		invoiceLine1.setInvoicedQuantity(additionalInvoicedQuantity);

		// Set AllowanceCharge for the line
		List<InvoiceLine.AllowanceCharge> additionalAllowanceCharges = new ArrayList<>();
		InvoiceLine.AllowanceCharge additionalAllowance = new InvoiceLine.AllowanceCharge();
		additionalAllowance.setChargeIndicator(false);

		InvoiceLine.Amount additionalAllowanceAmount = new InvoiceLine.Amount();
		additionalAllowanceAmount.setCurrencyID(currency);
		additionalAllowanceAmount.setValue("0");
		additionalAllowance.setAmount(additionalAllowanceAmount);

		InvoiceLine.AllowanceChargeReason additionalAllowanceReason = new InvoiceLine.AllowanceChargeReason();
		additionalAllowanceReason.setEn("Discount");
		additionalAllowanceReason.setAr("خصم");
		additionalAllowance.setAllowanceChargeReason(additionalAllowanceReason);

		additionalAllowanceCharges.add(additionalAllowance);
		invoiceLine1.setAllowanceCharge(additionalAllowanceCharges);

		// Set LineExtensionAmount
		String additionalTaxablePriceProduct = String.valueOf(netShippingAmount);
		InvoiceLine.LineExtensionAmount additionalLineExtensionAmount = new InvoiceLine.LineExtensionAmount();
		additionalLineExtensionAmount.setCurrencyID(currency);
		additionalLineExtensionAmount.setValue(additionalTaxablePriceProduct);
		invoiceLine1.setLineExtensionAmount(additionalLineExtensionAmount);

		double taxPercentage = 0.0;

		if (taxPercent != null) {
			taxPercentage = taxPercent.doubleValue();
		}
		BigDecimal calculatedTax = netShippingAmount
		.multiply(BigDecimal.valueOf(taxPercentage).divide(BigDecimal.valueOf(100)));

		InvoiceLine.TaxTotal additionalTaxTotal = new InvoiceLine.TaxTotal();

		InvoiceLine.TaxTotal.TaxAmount additionalTaxAmount = new InvoiceLine.TaxTotal.TaxAmount();
		additionalTaxAmount.setCurrencyID(currency);
		additionalTaxAmount.setValue(calculatedTax.setScale(2, RoundingMode.HALF_UP).toPlainString());
		additionalTaxTotal.setTaxAmount(additionalTaxAmount);

		BigDecimal totalPriceInclTaxProduct1 = BigDecimal.valueOf(shippingAmount).add(calculatedTax);
		String additionalTotalPriceInclTaxProduct = totalPriceInclTaxProduct1
		.setScale(2, RoundingMode.HALF_UP).toPlainString();

		InvoiceLine.TaxTotal.RoundingAmount additionalRoundingAmount = new InvoiceLine.TaxTotal.RoundingAmount();
		additionalRoundingAmount.setCurrencyID(currency);
		additionalRoundingAmount.setValue(additionalTotalPriceInclTaxProduct);
		additionalTaxTotal.setRoundingAmount(additionalRoundingAmount);
		invoiceLine1.setTaxTotal(additionalTaxTotal);

		invoiceLine1.setID("2");
		invoiceLines.add(invoiceLine1);

		BigDecimal additionalTaxablePrice = new BigDecimal(additionalTaxablePriceProduct);
		BigDecimal additionalTaxablePriceInclTax = new BigDecimal(additionalTotalPriceInclTaxProduct);
		totalPriceExclTax = totalPriceExclTax.add(additionalTaxablePrice);
		totalPriceInclTax = totalPriceInclTax.add(additionalTaxablePriceInclTax);
		}
		}
		}
		eInvoice.setInvoiceLine(invoiceLines);

		totalPriceExclTax = totalPriceExclTax.setScale(2, RoundingMode.HALF_UP);

		ZatcaInvoice.EInvoice.LegalMonetaryTotal LegalMonetaryTotal = new ZatcaInvoice.EInvoice.LegalMonetaryTotal();

		ZatcaInvoice.EInvoice.LineExtensionAmount LineExtensionAmount = new ZatcaInvoice.EInvoice.LineExtensionAmount();
		LineExtensionAmount.setCurrencyID(currency);
		LineExtensionAmount.setValue(parseNullStr(totalPriceExclTax));
		LegalMonetaryTotal.setLineExtensionAmount(LineExtensionAmount);

		ZatcaInvoice.EInvoice.TaxExclusiveAmount TaxExclusiveAmount = new ZatcaInvoice.EInvoice.TaxExclusiveAmount();
		TaxExclusiveAmount.setCurrencyID(currency);
		TaxExclusiveAmount.setValue(parseNullStr(totalPriceExclTax));
		LegalMonetaryTotal.setTaxExclusiveAmount(TaxExclusiveAmount);

		// ZatcaInvoice.EInvoice.TaxInclusiveAmount TaxInclusiveAmount = new
		// ZatcaInvoice.EInvoice.TaxInclusiveAmount();
		// TaxInclusiveAmount.setCurrencyID(currency);
		// TaxInclusiveAmount.setValue(parseNullStr(totalPriceInclTax.setScale(2,
		// RoundingMode.HALF_UP)));
		// LegalMonetaryTotal.setTaxInclusiveAmount(TaxInclusiveAmount);

		ZatcaInvoice.EInvoice.TaxAmount taxAmountS = new ZatcaInvoice.EInvoice.TaxAmount();

		setTaxTotalObject(invoiceLines, eInvoice, currency, store, taxAmountS);

		BigDecimal taxInclusiveAmountValue = totalPriceExclTax.add(new BigDecimal(taxAmountS.getValue()));

		ZatcaInvoice.EInvoice.TaxInclusiveAmount TaxInclusiveAmount = new ZatcaInvoice.EInvoice.TaxInclusiveAmount();
		TaxInclusiveAmount.setCurrencyID(currency);
		TaxInclusiveAmount.setValue(parseNullStr(taxInclusiveAmountValue.setScale(2, RoundingMode.HALF_UP)));
		LegalMonetaryTotal.setTaxInclusiveAmount(TaxInclusiveAmount);

		ZatcaInvoice.EInvoice.PaymentMeans PaymentMeans = new ZatcaInvoice.EInvoice.PaymentMeans();

		ZatcaInvoice.EInvoice.InstructionNote InstructionNote = new ZatcaInvoice.EInvoice.InstructionNote();
		InstructionNote.setAr("Refund");
		InstructionNote.setEn("Refund");

		PaymentMeans.setInstructionNote(InstructionNote);
		PaymentMeans.setPaymentMeansCode("1");

		List<ZatcaInvoice.EInvoice.PaymentMeans> PaymentMeansList = new ArrayList<>();
		PaymentMeansList.add(PaymentMeans);

		eInvoice.setPaymentMeans(PaymentMeansList);
		BigDecimal netPayableAmount = memo.getGrandTotal();

		LinkedHashMap<String, String> customFields = new LinkedHashMap<>();
		if (null != memo.getEasValueInCurrency() && memo.getEasValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
			
			customFields.put("Styli Cash",
					parseNullStr(memo.getEasValueInCurrency().setScale(2, RoundingMode.HALF_UP)));
			netPayableAmount = netPayableAmount.add(memo.getEasValueInCurrency());

		}

		if (null != memo.getShukranPointsRefundedValueInCurrency() && memo.getShukranPointsRefundedValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {

		customFields.put("Shukran Points",
		parseNullStr(memo.getShukranPointsRefundedValueInCurrency().setScale(2, RoundingMode.HALF_UP)));
		netPayableAmount = netPayableAmount.add(memo.getShukranPointsRefundedValueInCurrency());
		}
		if (null != memo.getAmstorecreditAmount() && memo.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) > 0) {
		customFields.put("Styli Credits",
		parseNullStr(memo.getAmstorecreditAmount().setScale(2, RoundingMode.HALF_UP)));
		netPayableAmount = netPayableAmount.add(memo.getAmstorecreditAmount());
		}
		customFields.put(PAYABLE_AMOUNT, parseNullStr(memo.getGrandTotal().setScale(2, RoundingMode.HALF_UP)));
		if(memo.getShukranPointsRefundedValueInCurrency() != null && memo.getShukranPointsRefundedValueInCurrency().compareTo(BigDecimal.ZERO)>0){
		customFields.put(PAYABLE_AMOUNT, parseNullStr(memo.getGrandTotal().add(memo.getShukranPointsRefundedValueInCurrency()).setScale(2, RoundingMode.HALF_UP)));
		}

		BigDecimal roundingAmount = netPayableAmount.subtract(taxInclusiveAmountValue);
		if (roundingAmount.compareTo(BigDecimal.ZERO) != 0) {
		customFields.put("Rounding Payable Amount", parseNullStr(roundingAmount.setScale(2, RoundingMode.HALF_UP)));

		ZatcaInvoice.EInvoice.PayableAmount PayableRoundingAmount = new ZatcaInvoice.EInvoice.PayableAmount();
		PayableRoundingAmount.setCurrencyID(currency);
		PayableRoundingAmount.setValue(parseNullStr(roundingAmount.setScale(2, RoundingMode.HALF_UP)));
		LegalMonetaryTotal.setPaybleRoundingAmount(PayableRoundingAmount);
		}

		String[] KSAStoreIds = { "1", "3" };
		if (!Arrays.stream(KSAStoreIds).anyMatch(str -> str.equals(store.getStoreId()))) {
		customFields.put("custom_header_en", zatcaConfig.getCustomHeaderTextCreditNote().getEn());
		customFields.put("custom_header_ar", zatcaConfig.getCustomHeaderTextCreditNote().getAr());
		}

		LOGGER.info("Net Payable Amount in Zata Credit Note: " + netPayableAmount);

		BigDecimal basePayableAmount = omsorderentityConverter.getBaseValueDecimal(netPayableAmount, storeToBaseRate);

		// Round the base payable amount to 2 decimal places
		BigDecimal payableAmount = basePayableAmount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();

		LOGGER.info("Base Payable Amount in Zata Credit Note: " + payableAmount);
		customFields.put("Base Payable Amount", payableAmount.toString());

		ZatcaInvoice.EInvoice.PayableAmount PayableAmount = new ZatcaInvoice.EInvoice.PayableAmount();
		PayableAmount.setCurrencyID(currency);
		PayableAmount.setValue(parseNullStr(netPayableAmount.setScale(2, RoundingMode.HALF_UP)));
		LegalMonetaryTotal.setPayableAmount(PayableAmount);

		eInvoice.setLegalMonetaryTotal(LegalMonetaryTotal);

		zatcaInvoice.setCustomFields(customFields);

		zatcaInvoice.setEInvoice(eInvoice);

		return zatcaInvoice;
	}


	public void setTaxTotalObject(List<InvoiceLine> invoiceLines, ZatcaInvoice.EInvoice eInvoice, String currency,
			Stores store, ZatcaInvoice.EInvoice.TaxAmount taxAmountS) {

		ZatcaInvoice.EInvoice.TaxTotal taxTotal = new ZatcaInvoice.EInvoice.TaxTotal();

		ZatcaInvoice.EInvoice.TaxAmount taxAmount = new ZatcaInvoice.EInvoice.TaxAmount();
		taxAmount.setCurrencyID(currency);// always SAR
		BigDecimal totalTaxAmount = BigDecimal.ZERO;

		// ZatcaInvoice.EInvoice.TaxAmount taxAmountS = new
		// ZatcaInvoice.EInvoice.TaxAmount();
		ZatcaInvoice.EInvoice.TaxAmount taxAmountE = new ZatcaInvoice.EInvoice.TaxAmount();
		ZatcaInvoice.EInvoice.TaxAmount taxAmountZ = new ZatcaInvoice.EInvoice.TaxAmount();

		taxAmountS.setCurrencyID(currency);
		taxAmountS.setValue("0.00");
		taxAmountE.setCurrencyID(currency);
		taxAmountE.setValue("0.00");
		taxAmountZ.setCurrencyID(currency);
		taxAmountZ.setValue("0.00");

		ZatcaInvoice.EInvoice.TaxableAmount taxableAmountS = new ZatcaInvoice.EInvoice.TaxableAmount();
		ZatcaInvoice.EInvoice.TaxableAmount taxableAmountE = new ZatcaInvoice.EInvoice.TaxableAmount();
		ZatcaInvoice.EInvoice.TaxableAmount taxableAmountZ = new ZatcaInvoice.EInvoice.TaxableAmount();

		taxableAmountS.setCurrencyID(currency);
		taxableAmountS.setValue("0.00");
		taxableAmountE.setCurrencyID(currency);
		taxableAmountE.setValue("0.00");
		taxableAmountZ.setCurrencyID(currency);
		taxableAmountZ.setValue("0.00");

		ZatcaInvoice.EInvoice.TaxCategory taxCategoryS = new ZatcaInvoice.EInvoice.TaxCategory();
		ZatcaInvoice.EInvoice.TaxCategory taxCategoryE = new ZatcaInvoice.EInvoice.TaxCategory();
		ZatcaInvoice.EInvoice.TaxCategory taxCategoryZ = new ZatcaInvoice.EInvoice.TaxCategory();

		ZatcaInvoice.EInvoice.TaxScheme TaxScheme = new ZatcaInvoice.EInvoice.TaxScheme();
		TaxScheme.setID("VAT");

		taxCategoryS.setID("S");
		taxCategoryS.setTaxScheme(TaxScheme);
		taxCategoryS.setPercent(parseNullStr(store.getTaxPercentage()));

		taxCategoryE.setID("O");
		taxCategoryE.setTaxScheme(TaxScheme);
		taxCategoryE.setPercent("0.00");

		taxCategoryZ.setID("Z");
		taxCategoryZ.setTaxScheme(TaxScheme);
		taxCategoryZ.setPercent("0.00");

		for (InvoiceLine invoiceLine : invoiceLines) {
			if (invoiceLine.getItem().getClassifiedTaxCategory().getID().equals("S")) {

				if (StringUtils.isNotEmpty(invoiceLine.getLineExtensionAmount().getValue())
						&& StringUtils.isNotBlank(invoiceLine.getLineExtensionAmount().getValue())) {
					taxableAmountS.setValue(parseNullStr(new BigDecimal(taxableAmountS.getValue())
							.add(new BigDecimal(invoiceLine.getLineExtensionAmount().getValue()))));
				}

				taxCategoryS.setPercent(invoiceLine.getItem().getClassifiedTaxCategory().getPercent());
			}
			if (invoiceLine.getItem().getClassifiedTaxCategory().getID().equals("Z")) {
				// taxAmountZ.setValue(parseNullStr(new BigDecimal(taxAmountZ.getValue())
				// .add(new BigDecimal(invoiceLine.getTaxTotal().getTaxAmount().getValue()))));
				if (StringUtils.isNotEmpty(invoiceLine.getLineExtensionAmount().getValue())
						&& StringUtils.isNotBlank(invoiceLine.getLineExtensionAmount().getValue())) {
					taxableAmountZ.setValue(parseNullStr(new BigDecimal(taxableAmountZ.getValue())
							.add(new BigDecimal(invoiceLine.getLineExtensionAmount().getValue()))));
				}
				taxCategoryZ.setPercent(invoiceLine.getItem().getClassifiedTaxCategory().getPercent());

				taxCategoryZ.setTaxExemptionReasonCode("VATEX-SA-34-4");

				ZatcaInvoice.EInvoice.TaxExemptionReason taxExemptionReason = new ZatcaInvoice.EInvoice.TaxExemptionReason();
				taxExemptionReason.setEn("Supply of a qualifying means of transport.");
				taxExemptionReason.setAr("");

				taxCategoryZ.setTaxExemptionReason(taxExemptionReason);

			}
			if (invoiceLine.getItem().getClassifiedTaxCategory().getID().equals("O")) {

				if (StringUtils.isNotEmpty(invoiceLine.getLineExtensionAmount().getValue())
						&& StringUtils.isNotBlank(invoiceLine.getLineExtensionAmount().getValue())) {
					taxableAmountE.setValue(parseNullStr(new BigDecimal(taxableAmountE.getValue())
							.add(new BigDecimal(invoiceLine.getLineExtensionAmount().getValue()))));
				}
				taxCategoryE.setPercent(invoiceLine.getItem().getClassifiedTaxCategory().getPercent());

				taxCategoryE.setTaxExemptionReasonCode("VATEX-SA-OOS");

				ZatcaInvoice.EInvoice.TaxExemptionReason taxExemptionReason = new ZatcaInvoice.EInvoice.TaxExemptionReason();
				taxExemptionReason.setEn("Donation is Exempted");
				taxExemptionReason.setAr("");

				taxCategoryE.setTaxExemptionReason(taxExemptionReason);
			}

		}

		List<ZatcaInvoice.EInvoice.TaxSubtotal> taxSubtotals = new ArrayList<>();

		if (!taxableAmountS.getValue().equals("0.00")) {
			ZatcaInvoice.EInvoice.TaxSubtotal taxSubtotalS = new ZatcaInvoice.EInvoice.TaxSubtotal();
			taxSubtotalS.setTaxableAmount(taxableAmountS);

			totalTaxAmount = new BigDecimal(taxableAmountS.getValue())
					.divide(new BigDecimal("100"), 4, BigDecimal.ROUND_HALF_UP);
			totalTaxAmount = totalTaxAmount.multiply(store.getTaxPercentage()).setScale(2, RoundingMode.HALF_UP);

			// taxableAmountS * store.getTaxPercentage()
			taxAmountS.setValue(parseNullStr(totalTaxAmount));

			taxSubtotalS.setTaxAmount(taxAmountS);
			taxSubtotalS.setTaxCategory(taxCategoryS);
			taxSubtotals.add(taxSubtotalS);
		}
		if (!taxableAmountE.getValue().equals("0.00")) {
			ZatcaInvoice.EInvoice.TaxSubtotal taxSubtotalE = new ZatcaInvoice.EInvoice.TaxSubtotal();
			taxSubtotalE.setTaxableAmount(taxableAmountE);
			taxSubtotalE.setTaxAmount(taxAmountE);
			taxSubtotalE.setTaxCategory(taxCategoryE);
			taxSubtotals.add(taxSubtotalE);
		}

		if (!taxableAmountZ.getValue().equals("0.00")) {
			ZatcaInvoice.EInvoice.TaxSubtotal taxSubtotalZ = new ZatcaInvoice.EInvoice.TaxSubtotal();
			taxSubtotalZ.setTaxableAmount(taxableAmountZ);
			taxSubtotalZ.setTaxAmount(taxAmountZ);
			taxSubtotalZ.setTaxCategory(taxCategoryZ);
			taxSubtotals.add(taxSubtotalZ);
		}

		taxAmount.setValue(parseNullStr(totalTaxAmount));

		taxTotal.setTaxAmount(taxAmount);

		taxTotal.setTaxSubtotal(taxSubtotals);

		List<ZatcaInvoice.EInvoice.TaxTotal> taxTotals = new ArrayList<>();

		taxTotals.add(taxTotal);
		String[] KSAStoreIds = { "1", "3" };
		if (!Arrays.stream(KSAStoreIds).anyMatch(str -> str.equals(store.getStoreId()))) {
			ZatcaInvoice.EInvoice.TaxTotal taxTotalNonKSA = new ZatcaInvoice.EInvoice.TaxTotal();
			ZatcaInvoice.EInvoice.TaxAmount taxAmountNonKSA = new ZatcaInvoice.EInvoice.TaxAmount();
			taxAmountNonKSA.setCurrencyID("SAR");// always SAR
			taxAmountNonKSA.setValue(BigDecimal.ZERO.toString());

			taxTotalNonKSA.setTaxAmount(taxAmountNonKSA);

			taxTotals.add(taxTotalNonKSA);
		}

		eInvoice.setTaxTotal(taxTotals);
	}

	public InvoiceLine getInvoiceLine(Integer lineID, String lineQuantity, InvoiceLine.Item.Name ItemName,
			InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory, String currency, String linetaxAmount,
			String linePriceInclTax, String linePriceExclTax, String SKU) {

		InvoiceLine invoiceLine = new InvoiceLine();
		invoiceLine.setID(lineID.toString());

		InvoiceLine.InvoicedQuantity InvoicedQuantity = new InvoiceLine.InvoicedQuantity();
		InvoicedQuantity.setValue(lineQuantity);
		invoiceLine.setInvoicedQuantity(InvoicedQuantity);

		InvoiceLine.Item Item = new InvoiceLine.Item();
		Item.setName(ItemName);

		InvoiceLine.Item.SellersItemIdentification sellersItemIdentification = new InvoiceLine.Item.SellersItemIdentification();
		//
		InvoiceLine.Item.SellersItemIdentification.ID sellersItemIdentificationID = new InvoiceLine.Item.SellersItemIdentification.ID();
		sellersItemIdentificationID.setAr(SKU);
		sellersItemIdentificationID.setEn(SKU);

		sellersItemIdentification.setID(sellersItemIdentificationID);
		Item.setSellersItemIdentification(sellersItemIdentification);

		InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme TaxScheme = new InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme();
		TaxScheme.setID("VAT");
		ClassifiedTaxCategory.setTaxScheme(TaxScheme);
		Item.setClassifiedTaxCategory(ClassifiedTaxCategory);
		invoiceLine.setItem(Item);

		InvoiceLine.TaxTotal TaxTotal = new InvoiceLine.TaxTotal();
		InvoiceLine.TaxTotal.TaxAmount TaxAmount = new InvoiceLine.TaxTotal.TaxAmount();
		TaxAmount.setCurrencyID(currency);
		TaxAmount.setValue(linetaxAmount);
		TaxTotal.setTaxAmount(TaxAmount);
		InvoiceLine.TaxTotal.RoundingAmount RoundingAmount = new InvoiceLine.TaxTotal.RoundingAmount();
		RoundingAmount.setCurrencyID(currency);
		RoundingAmount.setValue(linePriceInclTax);// Inclusive Tax
		TaxTotal.setRoundingAmount(RoundingAmount);
		invoiceLine.setTaxTotal(TaxTotal);

		String itemPriceExclTax = parseNullStr(new BigDecimal(linePriceExclTax)
				.divide(new BigDecimal(lineQuantity), 4, BigDecimal.ROUND_HALF_UP).setScale(2, RoundingMode.HALF_UP));

		InvoiceLine.Price Price = new InvoiceLine.Price();
		InvoiceLine.PriceAmount PriceAmount = new InvoiceLine.PriceAmount();
		PriceAmount.setCurrencyID(currency);
		PriceAmount.setValue(itemPriceExclTax);// Exclusive Tax
		Price.setPriceAmount(PriceAmount);
		invoiceLine.setPrice(Price);

		InvoiceLine.LineExtensionAmount LineExtensionAmount = new InvoiceLine.LineExtensionAmount();
		LineExtensionAmount.setCurrencyID(currency);
		LineExtensionAmount.setValue(linePriceExclTax);
		invoiceLine.setLineExtensionAmount(LineExtensionAmount);

		return invoiceLine;
	}

	@Async("asyncExecutor")
	@Transactional(propagation = Propagation.REQUIRED)
	public void checkStatusInvoice() {
		ZatcaConfig zatcaConfig = Constants.orderCredentials.getZatcaConfig();
		checkStatusInvoiceAsync(0, zatcaConfig);
	}

	@Transactional
	public void checkStatusInvoiceAsync(Integer offset, ZatcaConfig zatcaConfig) {
		try {

			Integer monthsAgo = (zatcaConfig.getZatcaInvoiceFailedListMonthsAgo() != null)
					? zatcaConfig.getZatcaInvoiceFailedListMonthsAgo()
					: 3;

			List<SalesInvoice> invoices = salesInvoiceRepository.findByZatcaNotGenerated(zatcaConfig.getBulkLimit(),
					offset, monthsAgo);
			if (invoices.size() == 0) {
				LOGGER.info("Zatca checkStatusInvoice Done");
				return;
			}
			List<ZatcaInvoiceBulkSingle> zatcaInvoiceBulk = new ArrayList<>();
			for (SalesInvoice invoice : invoices) {
				if (zatcaInvoiceBulk.stream().noneMatch(
						existingInvoice -> existingInvoice.getInvoiceNumber().equals(invoice.getIncrementId()))) {
					ZatcaInvoiceBulkSingle zatcaInvoiceBulkSingle = new ZatcaInvoiceBulkSingle();
					zatcaInvoiceBulkSingle.setInvoiceNumber(invoice.getIncrementId());
					zatcaInvoiceBulkSingle.setIssueDate(convertTimeZone(invoice.getCreatedAt(), 1, DATE_FORMAT));
					zatcaInvoiceBulkSingle.setInvoiceType("INV");
					zatcaInvoiceBulkSingle.setVat(zatcaConfig.getVatNo());
					zatcaInvoiceBulk.add(zatcaInvoiceBulkSingle);
				}
			}

			ZatcaBulkRequest zatcaBulkRequest = new ZatcaBulkRequest();
			zatcaBulkRequest.setInvoices(zatcaInvoiceBulk);

			String URL = zatcaConfig.getBaseUrl() + "/v3/einvoices/get-bulk";

			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
			requestHeaders.add("x-cleartax-auth-token", zatcaConfig.getClearTaxToken());
			requestHeaders.add("vat", zatcaConfig.getVatNo());

			HttpEntity<ZatcaBulkRequest> requestBody = new HttpEntity<>(zatcaBulkRequest, requestHeaders);
			LOGGER.info("Zatca checkStatusInvoice Body" + mapper.writeValueAsString(requestBody));
			ResponseEntity<BulkInvoiceResponse> response = restTemplate.exchange(URL, HttpMethod.POST, requestBody,
					BulkInvoiceResponse.class);
			List<String> errorInvoiceList = new ArrayList<>();

			if (response.getStatusCode() == HttpStatus.OK) {
				LOGGER.info("Zatca Body" + mapper.writeValueAsString(response.getBody()));
				if (null != response.getBody() && null == response.getBody().getErrorList()) {
					for (EInvoicesListRes invoiceRes : response.getBody().getEinvoicesList()) {
						if (null != invoiceRes.getEinvoiceStatus()
								&& invoiceRes.getEinvoiceStatus().equals("REPORTED")) {
							salesInvoiceRepository.updateZatcaStatus(invoiceRes.getEinvoiceStatus().toUpperCase(),
									invoiceRes.getInvoiceNumber());
						} else {
							LOGGER.info("Zatca Error Invoice: " + invoiceRes.getInvoiceNumber()
									+ mapper.writeValueAsString(invoiceRes));
							errorInvoiceList.add(invoiceRes.getInvoiceNumber());
						}
					}
					LOGGER.info("Zatca Error Invoice List: " + mapper.writeValueAsString(errorInvoiceList));
					if (errorInvoiceList.size() > 0) {
						String body = "Hi,\n" + "Please find the ZATCA failure Invoices List" + ".\n"
								+ String.join(", ", errorInvoiceList) + ".\n" + "Thanks";
						emailService.sendText(zatcaConfig.getTechSupportEmail(), env + " Zatca Invoice failed list",
								body);
					}
				}
				offset = offset + zatcaConfig.getBulkLimit();
				checkStatusInvoiceAsync(offset, zatcaConfig);
			} else {
				LOGGER.error("Zatca checkStatusInvoice Cleartax Status: " + response.getStatusCode());
			}
		} catch (Exception e) {
			LOGGER.error("exception occoured during Zatca checkStatusInvoice:" + e.getMessage());
		}
	}

	@Async("asyncExecutor")
	public void checkStatusCreditMemo() {
		ZatcaConfig zatcaConfig = Constants.orderCredentials.getZatcaConfig();
		checkStatusCreditMemoAsync(0, zatcaConfig);
	}
	
	@Transactional
	public void updateZatcaStatusTransactional(String status, String invoiceNumber) {
	    salesCreditmemoRepository.updateZatcaStatus(status, invoiceNumber);
	}

	public void checkStatusCreditMemoAsync(Integer offset, ZatcaConfig zatcaConfig) {
	    try {
	    	
	    	Integer monthsAgo = (zatcaConfig.getZatcaCreditmemoFailedListMonthsAgo() != null)
					? zatcaConfig.getZatcaCreditmemoFailedListMonthsAgo()
					: 3;
	    	
	        List<SalesCreditmemo> creditMemos = salesCreditmemoRepository
	                .findByZatcaNotGenerated(zatcaConfig.getBulkLimit(), offset,monthsAgo);

	        LOGGER.info("Credit Memos (IDs): {}" + creditMemos.stream()
	                .map(SalesCreditmemo::getIncrementId)
	                .collect(Collectors.toList()));

	        if (creditMemos.isEmpty()) {
	            LOGGER.info("Zatca checkStatusCreditMemo Done");
	            return;
	        }

	        List<ZatcaInvoiceBulkSingle> zatcaCreditBulk = new ArrayList<>();
	        Set<String> seenInvoiceIds = new HashSet<>();

	        for (SalesCreditmemo creditMemo : creditMemos) {
	            String incrementId = creditMemo.getIncrementId();
	            if (seenInvoiceIds.add(incrementId)) {
	                LOGGER.info("Zatca checkStatusCreditMemo Increment ID: {} " + incrementId + " Created AT: {}" + creditMemo.getCreatedAt());

	                ZatcaInvoiceBulkSingle bulkSingle = new ZatcaInvoiceBulkSingle();
	                bulkSingle.setInvoiceNumber(incrementId);
	                bulkSingle.setIssueDate(convertTimeZone(creditMemo.getCreatedAt(), 1, DATE_FORMAT));
	                bulkSingle.setInvoiceType("CRN");
	                bulkSingle.setVat(zatcaConfig.getVatNo());

	                zatcaCreditBulk.add(bulkSingle);
	            }
	        }

	        ZatcaBulkRequest zatcaBulkRequest = new ZatcaBulkRequest();
	        zatcaBulkRequest.setInvoices(zatcaCreditBulk);

	        String url = zatcaConfig.getBaseUrl() + "/v3/einvoices/get-bulk";

	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
	        headers.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
	        headers.add("x-cleartax-auth-token", zatcaConfig.getClearTaxToken());
	        headers.add("vat", zatcaConfig.getVatNo());

	        HttpEntity<ZatcaBulkRequest> requestBody = new HttpEntity<>(zatcaBulkRequest, headers);
	        LOGGER.info("Zatca checkStatusCreditMemo Body: {}" + mapper.writeValueAsString(requestBody));

	        ResponseEntity<BulkInvoiceResponse> response = restTemplate.exchange(
	                url, HttpMethod.POST, requestBody, BulkInvoiceResponse.class);

	        List<String> errorCreditMemoList = new ArrayList<>();

	        if (response.getStatusCode() == HttpStatus.OK) {
	            BulkInvoiceResponse body = response.getBody();
	            LOGGER.info("Zatca Response Body: {}" + mapper.writeValueAsString(body));

	            if (body != null && body.getErrorList() == null) {
	                for (EInvoicesListRes res : body.getEinvoicesList()) {
	                    if ("REPORTED".equalsIgnoreCase(res.getEinvoiceStatus())) {
	                        updateZatcaStatusTransactional(res.getEinvoiceStatus().toUpperCase(), res.getInvoiceNumber());
	                    } else {
	                        LOGGER.info("Zatca Error Invoice: {}, Details: {}" + mapper.writeValueAsString(res));
	                        errorCreditMemoList.add(res.getInvoiceNumber());
	                    }
	                }

	                LOGGER.info("Zatca Error Credit Memos List: {}" + mapper.writeValueAsString(errorCreditMemoList));
	                if (!errorCreditMemoList.isEmpty()) {
	                    String bodyText = "Hi,\nPlease find the ZATCA failure Credit memos.\n" +
	                            String.join(", ", errorCreditMemoList) + ".\nThanks";
	                    emailService.sendText(zatcaConfig.getTechSupportEmail(),
	                            env + " Zatca credit memo list", bodyText);
	                }
	            }
	            
	            checkStatusCreditMemoAsync(offset + zatcaConfig.getBulkLimit(), zatcaConfig);

	        } else {
	            LOGGER.error("Zatca checkStatusCreditMemo Cleartax Status: {}" + response.getStatusCode());
	        }

	    } catch (Exception e) {
	        LOGGER.error("Exception occurred during Zatca checkStatusCreditMemo: {}" + e.getMessage());
	    }
	}

	public AccountingSupplierParty getCustomerAddress(SalesOrder order) {
		AccountingSupplierParty accountingCustomerParty = new AccountingSupplierParty();
		AccountingSupplierParty.Party party = new AccountingSupplierParty.Party();
		AccountingSupplierParty.Party.PartyLegalEntity partyLegalEntity = new AccountingSupplierParty.Party.PartyLegalEntity();
		AccountingSupplierParty.Party.PartyLegalEntity.RegistrationName registrationName = new AccountingSupplierParty.Party.PartyLegalEntity.RegistrationName();

		String name = order.getCustomerFirstname();
		if (null != order.getCustomerLastname()) {
			name = name + order.getCustomerLastname();
		}

		registrationName.setAr(name);
		registrationName.setEn(name);
		partyLegalEntity.setRegistrationName(registrationName);
		party.setPartyLegalEntity(partyLegalEntity);

		SalesOrderAddress salesOrderAddress = salesOrderAddressRepository.findByOrderId(order.getEntityId()).stream()
				.filter(e -> e.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)).findFirst()
				.orElse(null);

		AccountingSupplierParty.Party.PostalAddress PostalAddress = new AccountingSupplierParty.Party.PostalAddress();

		AccountingSupplierParty.Party.PostalAddress.StreetName StreetName = new AccountingSupplierParty.Party.PostalAddress.StreetName();
		StreetName.setAr(salesOrderAddress.getStreet());
		StreetName.setEn(salesOrderAddress.getStreet());
		PostalAddress.setStreetName(StreetName);

		AccountingSupplierParty.Party.PostalAddress.CityName CityName = new AccountingSupplierParty.Party.PostalAddress.CityName();
		CityName.setAr(salesOrderAddress.getCity());
		CityName.setEn(salesOrderAddress.getCity());
		PostalAddress.setCityName(CityName);

		AccountingSupplierParty.Party.PostalAddress.CitySubdivisionName CitySubdivisionName = new AccountingSupplierParty.Party.PostalAddress.CitySubdivisionName();
		CitySubdivisionName.setAr(salesOrderAddress.getArea());
		CitySubdivisionName.setEn(salesOrderAddress.getArea());
		PostalAddress.setCitySubdivisionName(CitySubdivisionName);

		AccountingSupplierParty.Party.PostalAddress.CountrySubentity CountrySubentity = new AccountingSupplierParty.Party.PostalAddress.CountrySubentity();
		CountrySubentity.setAr(salesOrderAddress.getRegion());
		CountrySubentity.setEn(salesOrderAddress.getRegion());
		PostalAddress.setCountrySubentity(CountrySubentity);

		AccountingSupplierParty.Party.PostalAddress.Country Country = new AccountingSupplierParty.Party.PostalAddress.Country();
		Country.setIdentificationCode(salesOrderAddress.getCountryId());
		PostalAddress.setCountry(Country);

		party.setPostalAddress(PostalAddress);
		accountingCustomerParty.setParty(party);

		return accountingCustomerParty;
	}

	public String parseNullStr(Object val) {
		return (val == null) ? "0.00" : String.valueOf(val);
	}

	public String convertTimeZone(Timestamp datetime, Integer storeId, String format) {

		if (null != datetime) {
			Calendar calendar = Calendar.getInstance();

			calendar.setTime(new Date(datetime.getTime()));
			SimpleDateFormat sdf = new SimpleDateFormat(format);

			if (null != OrderConstants.timeZoneMap.get(storeId)) {
				sdf.setTimeZone(TimeZone.getTimeZone(OrderConstants.timeZoneMap.get(storeId)));
			} else {
				sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			}

			return sdf.format(calendar.getTime());
		} else {

			return null;
		}
	}

	public Stores getStoreById(Integer storeId) {
		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
				.orElse(null);
		return store;
	}

	public String getClassifiedTaxCategory(BigDecimal taxPercent) {
		if (taxPercent.compareTo(BigDecimal.ZERO) == 0) {
			return "Z";
		}
		return "S";
	}

	private ZatcaInvoice getSecondReturnInvoiceData(SalesOrder order, ZatcaConfig zatcaConfig,
			AmastyRmaRequest request) {
		ZatcaInvoice zatcaInvoice = new ZatcaInvoice();

		Stores store = getStoreById(order.getStoreId());

		String currency = store.getStoreCurrency();

		zatcaInvoice.setDeviceId(zatcaConfig.getDeviceId());

		ZatcaInvoice.EInvoice eInvoice = new ZatcaInvoice.EInvoice();
		eInvoice.setProfileID("reporting:1.0");

		ZatcaInvoice.EInvoice.ID ID = new ZatcaInvoice.EInvoice.ID();
		ID.setEn(request.getRmaIncId());
		ID.setAr(request.getRmaIncId());
		eInvoice.setID(ID);

		ZatcaInvoice.EInvoice.InvoiceTypeCode InvoiceTypeCode = new ZatcaInvoice.EInvoice.InvoiceTypeCode();
		InvoiceTypeCode.setName(zatcaConfig.getInvoiceTypeCode().getName());
		InvoiceTypeCode.setValue(zatcaConfig.getInvoiceTypeCode().getValue());
		eInvoice.setInvoiceTypeCode(InvoiceTypeCode);

		eInvoice.setIssueDate(convertTimeZone(new Timestamp(new Date().getTime()), 1, DATE_FORMAT));
		eInvoice.setIssueTime(convertTimeZone(new Timestamp(new Date().getTime()), 1, TIME_FORMAT));

		eInvoice.setTaxCurrencyCode("SAR");
		eInvoice.setDocumentCurrencyCode(currency);
		eInvoice.setAccountingSupplierParty(zatcaConfig.getAccountingSupplierParty());

		eInvoice.setAccountingCustomerParty(getCustomerAddress(order));

		List<InvoiceLine> invoiceLines = new ArrayList<>();

		BigDecimal totalAmountToBePaid = BigDecimal.valueOf(request.getReturnFee());
		// if(StringUtils.isNotEmpty(request.getReturnIncPayfortId()) &&
		// StringUtils.isNotBlank(request.getReturnIncPayfortId()) &&
		// request.getReturnInvoiceAmount()>0){
		// totalAmountToBePaid= BigDecimal.valueOf(request.getReturnInvoiceAmount());
		// }

		BigDecimal taxFactor = store.getTaxPercentage();
		BigDecimal divideValue = new BigDecimal(100);

		BigDecimal percentageFactor = divideValue.subtract(taxFactor);
		BigDecimal totalPriceInclTax = totalAmountToBePaid.setScale(2, RoundingMode.HALF_UP);
		BigDecimal totalPriceExclTax = percentageFactor.multiply(totalPriceInclTax)
				.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
		BigDecimal taxAmount = totalPriceInclTax.subtract(totalPriceExclTax).setScale(2, RoundingMode.HALF_UP);

		LOGGER.info("Tax Amount: " + taxAmount);
		LOGGER.info("Total Price Excluding Tax: " + totalPriceExclTax);
		LOGGER.info("Total Price Including Tax: " + totalPriceInclTax);

		InvoiceLine invoiceLine = new InvoiceLine();
		// set invoice line id
		invoiceLine.setID("1");
		// set invoice line item
		InvoiceLine.Item Item = new InvoiceLine.Item();
		// set invoice line name
		InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();
		ItemName.setEn("Extra Charges for Second Return");
		ItemName.setAr("رسوم إضافية للعودة الثانية");
		Item.setName(ItemName);
		// Set Invoice line ClassifiedTaxCategory
		InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
		ClassifiedTaxCategory.setID(this.getClassifiedTaxCategory(taxFactor));
		ClassifiedTaxCategory.setPercent(taxFactor.setScale(2, RoundingMode.HALF_UP).toString());
		InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme TaxScheme = new InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme();
		TaxScheme.setID("VAT");
		ClassifiedTaxCategory.setTaxScheme(TaxScheme);
		Item.setClassifiedTaxCategory(ClassifiedTaxCategory);
		// Set Invoice line sellersItemIdentification
		InvoiceLine.Item.SellersItemIdentification sellersItemIdentification = new InvoiceLine.Item.SellersItemIdentification();
		InvoiceLine.Item.SellersItemIdentification.ID sellersItemIdentificationID = new InvoiceLine.Item.SellersItemIdentification.ID();
		sellersItemIdentificationID.setAr("");
		sellersItemIdentificationID.setEn("");
		sellersItemIdentification.setID(sellersItemIdentificationID);
		Item.setSellersItemIdentification(sellersItemIdentification);
		invoiceLine.setItem(Item);

		// Set Invoice line InvoicedQuantity
		InvoiceLine.InvoicedQuantity InvoicedQuantity = new InvoiceLine.InvoicedQuantity();
		InvoicedQuantity.setValue("1");
		invoiceLine.setInvoicedQuantity(InvoicedQuantity);

		// Set Invoice Line Price
		InvoiceLine.Price Price = new InvoiceLine.Price();
		InvoiceLine.PriceAmount PriceAmount = new InvoiceLine.PriceAmount();
		PriceAmount.setCurrencyID(currency);
		PriceAmount.setValue(parseNullStr(totalPriceExclTax));// unit price Exclusive Tax
		Price.setPriceAmount(PriceAmount);
		invoiceLine.setPrice(Price);

		// set invoice line lineExtensionAmount
		InvoiceLine.LineExtensionAmount lineExtensionAmount = new InvoiceLine.LineExtensionAmount();
		lineExtensionAmount.setCurrencyID(currency);
		lineExtensionAmount.setValue(parseNullStr(totalPriceExclTax));// unit price Exclusive Tax
		invoiceLine.setLineExtensionAmount(lineExtensionAmount);

		// set invoice line tax total

		InvoiceLine.TaxTotal taxTotal = new InvoiceLine.TaxTotal();
		InvoiceLine.TaxTotal.TaxAmount TaxAmount = new InvoiceLine.TaxTotal.TaxAmount();
		TaxAmount.setCurrencyID(currency);
		TaxAmount.setValue(parseNullStr(taxAmount));
		taxTotal.setTaxAmount(TaxAmount);

		InvoiceLine.TaxTotal.RoundingAmount RoundingAmount = new InvoiceLine.TaxTotal.RoundingAmount();
		RoundingAmount.setCurrencyID(currency);
		RoundingAmount.setValue(parseNullStr(totalPriceInclTax));
		taxTotal.setRoundingAmount(RoundingAmount);
		invoiceLine.setTaxTotal(taxTotal);

		// Discount
		List<InvoiceLine.AllowanceCharge> AllowanceCharges = new ArrayList<>();
		InvoiceLine.AllowanceCharge AllowanceCharge = new InvoiceLine.AllowanceCharge();

		InvoiceLine.Amount AllowanceAmount = new InvoiceLine.Amount();
		AllowanceAmount.setCurrencyID(currency);
		AllowanceAmount.setValue("0");
		AllowanceCharge.setAmount(AllowanceAmount);

		InvoiceLine.AllowanceChargeReason AllowanceChargeReason = new InvoiceLine.AllowanceChargeReason();
		AllowanceChargeReason.setAr(zatcaConfig.getDiscountText().getAr());
		AllowanceChargeReason.setEn(zatcaConfig.getDiscountText().getEn());
		AllowanceCharge.setAllowanceChargeReason(AllowanceChargeReason);

		AllowanceCharge.setChargeIndicator(false);
		AllowanceCharges.add(AllowanceCharge);

		invoiceLine.setAllowanceCharge(AllowanceCharges);

		// taxable price after discount
		InvoiceLine.LineExtensionAmount LineExtensionAmount = new InvoiceLine.LineExtensionAmount();
		LineExtensionAmount.setCurrencyID(currency);
		LineExtensionAmount.setValue(parseNullStr(totalPriceExclTax));
		invoiceLine.setLineExtensionAmount(LineExtensionAmount);
		invoiceLines.add(invoiceLine);
		eInvoice.setInvoiceLine(invoiceLines);
		totalPriceExclTax = totalPriceExclTax.setScale(2, RoundingMode.HALF_UP);
		// set LegalMonetaryTotal for invoice
		// legal monetary line extension
		ZatcaInvoice.EInvoice.LegalMonetaryTotal LegalMonetaryTotal = new ZatcaInvoice.EInvoice.LegalMonetaryTotal();
		ZatcaInvoice.EInvoice.LineExtensionAmount LineExtensionAmount1 = new ZatcaInvoice.EInvoice.LineExtensionAmount();
		LineExtensionAmount1.setCurrencyID(currency);
		LineExtensionAmount1.setValue(parseNullStr(totalPriceExclTax));
		LegalMonetaryTotal.setLineExtensionAmount(LineExtensionAmount1);

		// legal monetary line tax exclusive amount
		ZatcaInvoice.EInvoice.TaxExclusiveAmount TaxExclusiveAmount = new ZatcaInvoice.EInvoice.TaxExclusiveAmount();
		TaxExclusiveAmount.setCurrencyID(currency);
		TaxExclusiveAmount.setValue(parseNullStr(totalPriceExclTax));
		LegalMonetaryTotal.setTaxExclusiveAmount(TaxExclusiveAmount);

		// legal monetary line tax inclusive amount
		ZatcaInvoice.EInvoice.TaxInclusiveAmount TaxInclusiveAmount = new ZatcaInvoice.EInvoice.TaxInclusiveAmount();
		TaxInclusiveAmount.setCurrencyID(currency);
		TaxInclusiveAmount.setValue(parseNullStr(totalPriceInclTax));
		LegalMonetaryTotal.setTaxInclusiveAmount(TaxInclusiveAmount);
		// legal monetary payable amount
		ZatcaInvoice.EInvoice.PayableAmount PayableAmount = new ZatcaInvoice.EInvoice.PayableAmount();
		PayableAmount.setCurrencyID(currency);
		PayableAmount.setValue(parseNullStr(totalPriceInclTax));
		LegalMonetaryTotal.setPayableAmount(PayableAmount);
		eInvoice.setLegalMonetaryTotal(LegalMonetaryTotal);

		LinkedHashMap<String, String> customFields = new LinkedHashMap<>();

		customFields.put(PAYABLE_AMOUNT, parseNullStr(totalPriceInclTax));
		customFields.put("Base Payable Amount", totalPriceExclTax.toString());

		String[] KSAStoreIds = { "1", "3" };
		if (!Arrays.stream(KSAStoreIds).anyMatch(str -> str.equals(store.getStoreId()))) {
			customFields.put("custom_header_en", zatcaConfig.getCustomHeaderTextInvoice().getEn());
			customFields.put("custom_header_ar", zatcaConfig.getCustomHeaderTextInvoice().getAr());
		}
		zatcaInvoice.setCustomFields(customFields);
		// set e invoice tax total

		List<ZatcaInvoice.EInvoice.TaxTotal> taxTotals = new ArrayList<>();
		if (currency.equals("SAR")) {
			ZatcaInvoice.EInvoice.TaxTotal taxTotal2 = new ZatcaInvoice.EInvoice.TaxTotal();
			ZatcaInvoice.EInvoice.TaxSubtotal taxSubtotal = new ZatcaInvoice.EInvoice.TaxSubtotal();
			ZatcaInvoice.EInvoice.TaxAmount taxAmount1 = new ZatcaInvoice.EInvoice.TaxAmount();
			taxAmount1.setCurrencyID(currency);
			taxAmount1.setValue(parseNullStr(taxAmount));
			taxTotal2.setTaxAmount(taxAmount1);
			// set tax sub total
			List<ZatcaInvoice.EInvoice.TaxSubtotal> taxSubtotals = new ArrayList<>();

			ZatcaInvoice.EInvoice.TaxableAmount taxableAmount = new ZatcaInvoice.EInvoice.TaxableAmount();
			taxableAmount.setCurrencyID(currency);
			taxableAmount.setValue(parseNullStr(totalPriceExclTax));
			taxSubtotal.setTaxableAmount(taxableAmount);
			ZatcaInvoice.EInvoice.TaxAmount taxAmount2 = new ZatcaInvoice.EInvoice.TaxAmount();
			taxAmount2.setCurrencyID(currency);
			taxAmount2.setValue(parseNullStr(taxAmount));
			taxSubtotal.setTaxAmount(taxAmount2);
			// set tax category
			ZatcaInvoice.EInvoice.TaxCategory taxCategory = new ZatcaInvoice.EInvoice.TaxCategory();
			taxCategory.setID("S");

			taxCategory.setPercent(parseNullStr(taxFactor));
			ZatcaInvoice.EInvoice.TaxScheme taxScheme = new ZatcaInvoice.EInvoice.TaxScheme();
			taxScheme.setID("VAT");
			taxCategory.setTaxScheme(taxScheme);
			taxSubtotal.setTaxCategory(taxCategory);
			taxSubtotals.add(taxSubtotal);
			taxTotal2.setTaxSubtotal(taxSubtotals);
			taxTotals.add(taxTotal2);
			eInvoice.setTaxTotal(taxTotals);
		} else {

			ZatcaInvoice.EInvoice.TaxableAmount taxableAmount = new ZatcaInvoice.EInvoice.TaxableAmount();
			taxableAmount.setCurrencyID(currency);
			taxableAmount.setValue(parseNullStr(totalPriceExclTax));

			ZatcaInvoice.EInvoice.TaxCategory taxCategory = new ZatcaInvoice.EInvoice.TaxCategory();
			taxCategory.setID("Z");
			taxCategory.setPercent(String.valueOf(taxFactor));
			taxCategory.setTaxExemptionReasonCode("VATEX-SA-34-4");
			ZatcaInvoice.EInvoice.TaxScheme taxScheme = new ZatcaInvoice.EInvoice.TaxScheme();
			taxCategory.setTaxScheme(taxScheme);
			taxScheme.setID("VAT");
			ZatcaInvoice.EInvoice.TaxExemptionReason taxExemptionReason = new ZatcaInvoice.EInvoice.TaxExemptionReason();
			taxExemptionReason.setAr("");
			taxExemptionReason.setEn("Supply of a qualifying means of transport.");
			taxCategory.setTaxExemptionReason(taxExemptionReason);

			ZatcaInvoice.EInvoice.TaxSubtotal taxSubtotal = new ZatcaInvoice.EInvoice.TaxSubtotal();
			taxSubtotal.setTaxableAmount(taxableAmount);
			taxSubtotal.setTaxCategory(taxCategory);
			ZatcaInvoice.EInvoice.TaxTotal taxTotal1 = new ZatcaInvoice.EInvoice.TaxTotal();
			taxTotal1.setTaxSubtotal(Collections.singletonList(taxSubtotal));

			ZatcaInvoice.EInvoice.TaxAmount taxAmount1 = new ZatcaInvoice.EInvoice.TaxAmount();
			taxAmount1.setCurrencyID("SAR");
			taxAmount1.setValue("0");
			ZatcaInvoice.EInvoice.TaxTotal taxTotal2 = new ZatcaInvoice.EInvoice.TaxTotal();

			taxTotal2.setTaxAmount(taxAmount1);
			eInvoice.setTaxTotal(Arrays.asList(taxTotal1, taxTotal2));
		}

		zatcaInvoice.setEInvoice(eInvoice);

		try {

			ObjectMapper objectMapper = new ObjectMapper();
			LOGGER.info("invoice Lines Data: " + objectMapper.writeValueAsString(zatcaInvoice));
		} catch (JsonProcessingException ex) {
			LOGGER.info("Error Processing Invoice Line" + ex);
		}
		return zatcaInvoice;
	}


	ZatcaInvoice codRtoZatcaInvoice(SalesOrder order, ZatcaConfig zatcaConfig){
		SalesInvoice invoice= order.getSalesInvoices().stream().findFirst().orElse(null);
		ZatcaInvoice zatcaInvoice = generateZatcaInvoice(invoice, order, zatcaConfig, true);

		return zatcaInvoice;
	}

}