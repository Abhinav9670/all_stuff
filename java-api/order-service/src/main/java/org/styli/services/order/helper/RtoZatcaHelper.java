package org.styli.services.order.helper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.sales.SalesCreditmemoItem;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.zatca.InvoiceLine;
import org.styli.services.order.pojo.zatca.ZatcaConfig;
import org.styli.services.order.pojo.zatca.ZatcaInvoice;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderItemRepository;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.utility.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;

@Component
public class RtoZatcaHelper {
    private static final Log LOGGER = LogFactory.getLog(RtoZatcaHelper.class);
    private static final String PAYABLE_AMOUNT="Payable Amount";
    public static final String DATE_FORMAT= "yyyy-MM-dd";
    public static final String TIME_FORMAT= "HH:mm:ss";

    @Autowired
    RefundHelper refundHelper;

    @Autowired
    @Lazy
    ZatcaServiceImpl zatcaServiceImpl;

    @Autowired
    SalesCreditmemoRepository salesCreditmemoRepository;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Autowired
    MulinHelper mulinHelper;

    @Autowired
    SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    SplitSalesOrderItemRepository splitSalesOrderItemRepository;

    @Autowired
    OmsorderentityConverter omsorderentityConverter;

    public void generateRtoCreditMemo(SalesOrder salesOrder, SplitSalesOrder splitSalesOrder, Stores store, List<SalesCreditmemoItem> memoItems, BigDecimal totalAmountToRefundAsCredit, BigDecimal totalAmountToRefundOnline, BigDecimal totalSubTotalIncTax, BigDecimal totalSubTotalExclTax, BigDecimal totalTaxAmount, BigDecimal totalDiscountAmountForOrder, BigDecimal totalAmountToRefund, BigDecimal taxFactor, BigDecimal totalTaxableAmount) {
        String incrementId = salesOrder != null ? salesOrder.getIncrementId() : splitSalesOrder.getIncrementId();
        Integer storeId = salesOrder != null ? salesOrder.getStoreId() : splitSalesOrder.getStoreId();
        
        LOGGER.info("Inside generateCreditMemo: " + incrementId);
        String memoIncrementId = refundHelper.getIncrementId(storeId);
        SalesCreditmemo memo = createRtoCreditMemo(salesOrder, splitSalesOrder, totalAmountToRefundAsCredit, totalAmountToRefundOnline, true, memoIncrementId, totalSubTotalIncTax, totalSubTotalExclTax, totalTaxAmount, totalDiscountAmountForOrder);
        refundHelper.createRtoCreditmemoItems(memo, memoItems);
        // ZATCA start creditMemo
        if(Constants.getZatcaFlag(storeId)) {
            LOGGER.info("Inside zatca generateCreditMemo call : " + incrementId);
            SalesInvoice invoice = (salesOrder != null ? salesOrder.getSalesInvoices() : splitSalesOrder.getSalesInvoices()).stream().findFirst().orElse(null);
            zatcaServiceImpl.sendZatcaCreditMemo(memo, invoice, salesOrder, splitSalesOrder, true, store, taxFactor, memoItems, totalTaxableAmount);
        }
        // ZATCA Ends creditMemo

    }

    public SalesCreditmemo createRtoCreditMemo(SalesOrder order, SplitSalesOrder splitSalesOrder, BigDecimal totalAmountToRefundAsCredit, BigDecimal totalAmountToRefundOnline, boolean isFullCancelled, String memoIncrementId, BigDecimal totalSubTotalIncTax, BigDecimal totalSubTotalExclTax, BigDecimal totalTaxAmount, BigDecimal totalDiscountAmountForOrder) {

        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal baseGrandTotal = BigDecimal.ZERO;

        BigDecimal adjustment = BigDecimal.ZERO;
        BigDecimal baseAdjustMent = BigDecimal.ZERO;

        BigDecimal adjustmentNegative = BigDecimal.ZERO;
        BigDecimal baseAdjustMentNegative = BigDecimal.ZERO;

        BigDecimal amastyStoreCreditAmount = BigDecimal.ZERO;
        BigDecimal baseAmastyStoreCreditAmount = BigDecimal.ZERO;

        if(order != null){
            if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()
                    && isFullCancelled) {
                adjustment = order.getSubSalesOrder().getDonationAmount().negate();
                baseAdjustMent = order.getSubSalesOrder().getBaseDonationAmount().negate();
                adjustmentNegative = order.getSubSalesOrder().getDonationAmount();
                baseAdjustMentNegative = order.getSubSalesOrder().getBaseDonationAmount();
            }
        }else{
            if(splitSalesOrder.getSplitSubSalesOrder() != null && null != splitSalesOrder.getSplitSubSalesOrder().getDonationAmount()
            && isFullCancelled){
                adjustment = splitSalesOrder.getSplitSubSalesOrder().getDonationAmount().negate();
                baseAdjustMent = splitSalesOrder.getSplitSubSalesOrder().getBaseDonationAmount().negate();
            }
        }

        BigDecimal storeToBaseRate = order != null ? order.getStoreToBaseRate() : splitSalesOrder.getStoreToBaseRate();
        BigDecimal baseToGlobalRate = order != null ? order.getBaseToGlobalRate() : splitSalesOrder.getBaseToGlobalRate();
        BigDecimal storeToOrderRate = order != null ? order.getStoreToOrderRate() : splitSalesOrder.getStoreToOrderRate();
        BigDecimal baseToOrderRate = order != null ? order.getBaseToOrderRate() : splitSalesOrder.getBaseToOrderRate();
        BigDecimal baseShippingTaxAmount = order != null ? order.getBaseShippingTaxAmount() : splitSalesOrder.getBaseShippingTaxAmount();
        BigDecimal baseShippingInclTax = order != null ? order.getBaseShippingInclTax() : splitSalesOrder.getBaseShippingInclTax();
        BigDecimal baseShippingAmount = order != null ? order.getBaseShippingAmount() : splitSalesOrder.getBaseShippingAmount();
        BigDecimal shippingAmount = order != null ? order.getShippingAmount() : splitSalesOrder.getShippingAmount();
        BigDecimal shippingTaxAmount = order != null ? order.getShippingTaxAmount() : splitSalesOrder.getShippingTaxAmount();
        BigDecimal shippingInclTax = order != null ? order.getShippingInclTax() : splitSalesOrder.getShippingInclTax();
        Integer shippingAddressId = order != null ? order.getShippingAddressId() : splitSalesOrder.getShippingAddressId();
        Integer billingAddressId = order != null ? order.getBillingAddressId() : splitSalesOrder.getBillingAddressId();
        String baseCurrencyCode = order != null ? order.getBaseCurrencyCode() : splitSalesOrder.getBaseCurrencyCode();
        String globalCurrencyCode = order != null ? order.getGlobalCurrencyCode() : splitSalesOrder.getGlobalCurrencyCode();
        String orderCurrencyCode = order != null ? order.getOrderCurrencyCode() : splitSalesOrder.getOrderCurrencyCode();
        String storeCurrencyCode = order != null ? order.getStoreCurrencyCode() : splitSalesOrder.getStoreCurrencyCode();
        Integer storeId = order != null ? order.getStoreId() : splitSalesOrder.getStoreId();
        Integer entityId = order != null ? order.getEntityId() : splitSalesOrder.getEntityId();
        String couponCode = order != null ? order.getCouponCode() : splitSalesOrder.getCouponCode();
        BigDecimal cashOnDeliveryFee = order != null ? order.getCashOnDeliveryFee() : splitSalesOrder.getCashOnDeliveryFee();
        BigDecimal baseCashOnDeliveryFee = order != null ? order.getBaseCashOnDeliveryFee() : splitSalesOrder.getBaseCashOnDeliveryFee();

        amastyStoreCreditAmount = totalAmountToRefundAsCredit;
        baseAmastyStoreCreditAmount = amastyStoreCreditAmount.multiply(storeToBaseRate)
                .setScale(4, RoundingMode.HALF_UP);


        if (null != totalAmountToRefundOnline) {

            grandTotal = totalAmountToRefundOnline;

            baseGrandTotal = totalAmountToRefundOnline.multiply(storeToBaseRate).setScale(4, RoundingMode.HALF_UP);
        }

        SalesCreditmemo memo = new SalesCreditmemo();
        memo.setStoreId(storeId);
        memo.setBaseShippingTaxAmount(baseShippingTaxAmount);
        memo.setStoreToOrderRate(storeToOrderRate);

        memo.setBaseToOrderRate(baseToOrderRate);

        memo.setGrandTotal(grandTotal);
        memo.setBaseGrandTotal(baseGrandTotal);

        memo.setSubtotalInclTax(totalSubTotalIncTax);
        memo.setBaseSubtotalInclTax(totalSubTotalIncTax.multiply(storeToBaseRate).setScale(2, RoundingMode.HALF_UP));
        memo.setSubtotal(totalSubTotalExclTax);
        memo.setBaseSubtotal(totalSubTotalExclTax.multiply(storeToBaseRate).setScale(2, RoundingMode.HALF_UP));
        memo.setDiscountAmount(totalDiscountAmountForOrder);
        memo.setBaseDiscountAmount(totalDiscountAmountForOrder.multiply(storeToBaseRate).setScale(2, RoundingMode.HALF_UP));
        memo.setTaxAmount(totalTaxAmount);
        memo.setBaseTaxAmount(totalTaxAmount.multiply(storeToBaseRate).setScale(2, RoundingMode.HALF_UP));
        
        if(order != null){
            if (order.getSubSalesOrder() != null){
                if (order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
                    memo.setShukranPointsRefunded(order.getSubSalesOrder().getTotalShukranCoinsBurned());
                    memo.setShukranPointsRefundedValueInCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());
                    memo.setShukranPointsRefundedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
                }
                if(order.getSubSalesOrder().getEasValueInCurrency() != null && order.getSubSalesOrder().getEasValueInCurrency().compareTo(BigDecimal.ZERO)>0){
                    memo.setEasCoins(order.getSubSalesOrder().getEasCoins());
                    memo.setEasValueInCurrency(order.getSubSalesOrder().getEasValueInCurrency());
                    memo.setEasValueInBaseCurrency(order.getSubSalesOrder().getEasValueInBaseCurrency());
                }
            }
        }else{
            if(splitSalesOrder.getSplitSubSalesOrder() != null){
                if(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0) {
                    memo.setShukranPointsRefunded(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned());
                    memo.setShukranPointsRefundedValueInCurrency(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency());
                    memo.setShukranPointsRefundedValueInBaseCurrency(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
                }
                if(splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency() != null && splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency().compareTo(BigDecimal.ZERO)>0){
                    memo.setEasCoins(splitSalesOrder.getSplitSubSalesOrder().getEasCoins());
                    memo.setEasValueInCurrency(splitSalesOrder.getSplitSubSalesOrder().getEasValueInCurrency());
                    memo.setEasValueInBaseCurrency(splitSalesOrder.getSplitSubSalesOrder().getEasValueInBaseCurrency());
                }
            }
        }
        if(isFullCancelled) {
            memo.setShippingAmount(shippingAmount);
            memo.setBaseShippingAmount(baseShippingAmount);
            memo.setShippingTaxAmount(shippingTaxAmount);
            memo.setShippingInclTax(shippingInclTax);

        }
        memo.setStoreToBaseRate(storeToBaseRate);
        memo.setBaseToGlobalRate(baseToGlobalRate);
        memo.setBaseAdjustment(baseAdjustMent);



        memo.setAdjustment(adjustment);


        if(order != null){
            memo.setOrderId(entityId);
        } else {
            memo.setOrderId(splitSalesOrder.getSalesOrder().getEntityId());
        }
        
        if(splitSalesOrder != null){
            memo.setSplitOrderId(splitSalesOrder.getEntityId());
        }

        memo.setState(2);
        memo.setShippingAddressId(shippingAddressId);
        memo.setBillingAddressId(billingAddressId);
        memo.setBaseCurrencyCode(baseCurrencyCode);
        memo.setGlobalCurrencyCode(globalCurrencyCode);
        memo.setOrderCurrencyCode(orderCurrencyCode);
        memo.setStoreCurrencyCode(storeCurrencyCode);
        memo.setIncrementId(memoIncrementId);
        memo.setCreatedAt(new Timestamp(new Date().getTime()));
        memo.setUpdatedAt(new Timestamp(new Date().getTime()));

        memo.setBaseShippingInclTax(baseShippingInclTax);
        memo.setDiscountDescription(couponCode);
        memo.setCashOnDeliveryFee(cashOnDeliveryFee);
        memo.setBaseCashOnDeliveryFee(baseCashOnDeliveryFee);
        memo.setAmstorecreditAmount(amastyStoreCreditAmount);
        memo.setAmstorecreditBaseAmount(baseAmastyStoreCreditAmount);
        memo.setRmaNumber(entityId.toString());

        memo.setAdjustmentNegative(adjustmentNegative);
        memo.setBaseAdjustmentNegative(baseAdjustMentNegative);

        salesCreditmemoRepository.saveAndFlush(memo);

        return memo;
    }

    public ZatcaInvoice generateRtoZatcaCreditMemo(SalesCreditmemo memo, SalesInvoice invoice, SalesOrder order, SplitSalesOrder splitSalesOrder,
                                                   ZatcaConfig zatcaConfig, Stores store, BigDecimal taxFactor, List<SalesCreditmemoItem> memoItems, BigDecimal totalTaxableAmount) {
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

        eInvoice.setIssueDate(zatcaServiceImpl.convertTimeZone(memo.getCreatedAt(), 1, DATE_FORMAT));
        eInvoice.setIssueTime(zatcaServiceImpl.convertTimeZone(memo.getCreatedAt(), 1, TIME_FORMAT));

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

        eInvoice.setAccountingCustomerParty(zatcaServiceImpl.getCustomerAddress(order != null ? order : splitSalesOrder.getSalesOrder()));

        List<SalesOrderItem> salesOrderItems = new ArrayList<>();
        List<SplitSalesOrderItem> splitSalesOrderItems = new ArrayList<>();

        if(order != null){
            salesOrderItems = salesOrderItemRepository
                .findSalesOrderItemConfigurableByOrderId(memo.getOrderId());
        }else{
            splitSalesOrderItems = splitSalesOrderItemRepository
                .findSalesOrderItemConfigurableByOrderId(memo.getSplitOrderId());
        }

        Map<String, ProductResponseBody> productsFromMulin = order != null ? mulinHelper
                .getMulinProductsFromOrder(Collections.singletonList(order), restTemplate) 
                : mulinHelper.getMulinProductsFromSplitOrder(Collections.singletonList(splitSalesOrder), restTemplate);
        
        List<InvoiceLine> invoiceLines = order != null ? returnRtoZatcaInvoiceLines(zatcaConfig, taxFactor, memoItems, salesOrderItems, productsFromMulin, currency) 
            : returnRtoZatcaInvoiceLinesForSplitOrder(zatcaConfig, taxFactor, memoItems, splitSalesOrderItems, productsFromMulin, currency);
        
        eInvoice.setInvoiceLine(invoiceLines);

        ZatcaInvoice.EInvoice.LegalMonetaryTotal LegalMonetaryTotal = new ZatcaInvoice.EInvoice.LegalMonetaryTotal();

        ZatcaInvoice.EInvoice.LineExtensionAmount LineExtensionAmount = new ZatcaInvoice.EInvoice.LineExtensionAmount();
        LineExtensionAmount.setCurrencyID(currency);
        LineExtensionAmount.setValue(zatcaServiceImpl.parseNullStr(totalTaxableAmount));
        LegalMonetaryTotal.setLineExtensionAmount(LineExtensionAmount);

        ZatcaInvoice.EInvoice.TaxExclusiveAmount TaxExclusiveAmount = new ZatcaInvoice.EInvoice.TaxExclusiveAmount();
        TaxExclusiveAmount.setCurrencyID(currency);
        TaxExclusiveAmount.setValue(zatcaServiceImpl.parseNullStr(totalTaxableAmount));
        LegalMonetaryTotal.setTaxExclusiveAmount(TaxExclusiveAmount);

        ZatcaInvoice.EInvoice.TaxAmount taxAmountS = new ZatcaInvoice.EInvoice.TaxAmount();

        zatcaServiceImpl.setTaxTotalObject(invoiceLines, eInvoice, currency, store, taxAmountS);

        BigDecimal taxInclusiveAmountValue = totalTaxableAmount.add(new BigDecimal(taxAmountS.getValue()));

        ZatcaInvoice.EInvoice.TaxInclusiveAmount TaxInclusiveAmount = new ZatcaInvoice.EInvoice.TaxInclusiveAmount();
        TaxInclusiveAmount.setCurrencyID(currency);
        TaxInclusiveAmount.setValue(zatcaServiceImpl.parseNullStr(taxInclusiveAmountValue.setScale(2, RoundingMode.HALF_UP)));
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
                    zatcaServiceImpl.parseNullStr(memo.getEasValueInCurrency().setScale(2, RoundingMode.HALF_UP)));
            netPayableAmount = netPayableAmount.add(memo.getEasValueInCurrency());
        }

        if (null != memo.getShukranPointsRefundedValueInCurrency() && memo.getShukranPointsRefundedValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {

            customFields.put("Shukran Points",
                    zatcaServiceImpl.parseNullStr(memo.getShukranPointsRefundedValueInCurrency().setScale(2, RoundingMode.HALF_UP)));
            netPayableAmount = netPayableAmount.add(memo.getShukranPointsRefundedValueInCurrency());
        }
        if (null != memo.getAmstorecreditAmount() && memo.getAmstorecreditAmount().compareTo(BigDecimal.ZERO) > 0) {
            customFields.put("Styli Credits",
                    zatcaServiceImpl.parseNullStr(memo.getAmstorecreditAmount().setScale(2, RoundingMode.HALF_UP)));
            netPayableAmount = netPayableAmount.add(memo.getAmstorecreditAmount());
        }
        customFields.put(PAYABLE_AMOUNT, zatcaServiceImpl.parseNullStr(memo.getGrandTotal().setScale(2, RoundingMode.HALF_UP)));
        if(memo.getShukranPointsRefundedValueInCurrency() != null && memo.getShukranPointsRefundedValueInCurrency().compareTo(BigDecimal.ZERO)>0){
            customFields.put(PAYABLE_AMOUNT, zatcaServiceImpl.parseNullStr(memo.getGrandTotal().add(memo.getShukranPointsRefundedValueInCurrency()).setScale(2, RoundingMode.HALF_UP)));
        }

        BigDecimal roundingAmount = netPayableAmount.subtract(taxInclusiveAmountValue);
        if (roundingAmount.compareTo(BigDecimal.ZERO) != 0) {
            customFields.put("Rounding Payable Amount", zatcaServiceImpl.parseNullStr(roundingAmount.setScale(2, RoundingMode.HALF_UP)));

            ZatcaInvoice.EInvoice.PayableAmount PayableRoundingAmount = new ZatcaInvoice.EInvoice.PayableAmount();
            PayableRoundingAmount.setCurrencyID(currency);
            PayableRoundingAmount.setValue(zatcaServiceImpl.parseNullStr(roundingAmount.setScale(2, RoundingMode.HALF_UP)));
            LegalMonetaryTotal.setPaybleRoundingAmount(PayableRoundingAmount);
        }

        String[] KSAStoreIds = { "1", "3" };
        if (Arrays.stream(KSAStoreIds).noneMatch(str -> str.equals(store.getStoreId()))) {
            customFields.put("custom_header_en", zatcaConfig.getCustomHeaderTextCreditNote().getEn());
            customFields.put("custom_header_ar", zatcaConfig.getCustomHeaderTextCreditNote().getAr());
        }

        LOGGER.info("Net Payable Amount in Zata Credit Note: " + netPayableAmount);
        
        BigDecimal storeToBaseRate = order != null ? order.getStoreToBaseRate() : splitSalesOrder.getStoreToBaseRate();

        BigDecimal basePayableAmount = omsorderentityConverter.getBaseValueDecimal(netPayableAmount,
                storeToBaseRate);

        // Round the base payable amount to 2 decimal places
        BigDecimal payableAmount = basePayableAmount.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();

        LOGGER.info("Base Payable Amount in Zata Credit Note: " + payableAmount);
        customFields.put("Base Payable Amount", payableAmount.toString());

        ZatcaInvoice.EInvoice.PayableAmount PayableAmount = new ZatcaInvoice.EInvoice.PayableAmount();
        PayableAmount.setCurrencyID(currency);
        PayableAmount.setValue(zatcaServiceImpl.parseNullStr(netPayableAmount.setScale(2, RoundingMode.HALF_UP)));
        LegalMonetaryTotal.setPayableAmount(PayableAmount);

        eInvoice.setLegalMonetaryTotal(LegalMonetaryTotal);

        zatcaInvoice.setCustomFields(customFields);

        zatcaInvoice.setEInvoice(eInvoice);

        return zatcaInvoice;
    }

    public List<InvoiceLine> returnRtoZatcaInvoiceLines(ZatcaConfig zatcaConfig, BigDecimal taxFactor, List<SalesCreditmemoItem> memoItems, List<SalesOrderItem> salesOrderItems, Map<String, ProductResponseBody> productsFromMulin, String currency){
        List<InvoiceLine> invoiceLines = new ArrayList<>();
        int lineCount = 1;
        for (SalesCreditmemoItem memoItem : memoItems) {
            SalesOrderItem orderItem = salesOrderItems.stream()
                    .filter(e -> e.getItemId().equals(memoItem.getOrderItemId())).findAny().orElse(null);
            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setID(String.valueOf(lineCount));

            InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();

            ItemName.setEn(memoItem.getName());
            ItemName.setAr(memoItem.getName());
            if (orderItem != null && orderItem.getParentSku() != null && productsFromMulin.containsKey(orderItem.getParentSku())) {
                ProductResponseBody productDetailsMulin = productsFromMulin.get(orderItem.getParentSku());
                if (null != productDetailsMulin.getAttributes().getName().getArabic()) {
                    ItemName.setAr(productDetailsMulin.getAttributes().getName().getArabic());
                    ItemName.setEn(productDetailsMulin.getAttributes().getName().getEnglish());
                }
            }

            InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
            if(orderItem != null && orderItem.getTaxPercent() != null) {
                ClassifiedTaxCategory.setID(zatcaServiceImpl.getClassifiedTaxCategory(orderItem.getTaxPercent()));
                ClassifiedTaxCategory.setPercent(orderItem.getTaxPercent().setScale(2, RoundingMode.HALF_UP).toString());
            }

            InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme TaxScheme = new InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme();

            TaxScheme.setID("VAT");
            ClassifiedTaxCategory.setTaxScheme(TaxScheme);
            InvoiceLine.InvoicedQuantity InvoicedQuantity = new InvoiceLine.InvoicedQuantity();
            InvoicedQuantity.setValue(memoItem.getQty().setScale(2, RoundingMode.HALF_UP).toString());
            invoiceLine.setInvoicedQuantity(InvoicedQuantity);

            InvoiceLine.Item Item = new InvoiceLine.Item();
            Item.setName(ItemName);

            InvoiceLine.Item.SellersItemIdentification sellersItemIdentification = new InvoiceLine.Item.SellersItemIdentification();
            InvoiceLine.Item.SellersItemIdentification.ID sellersItemIdentificationID = new InvoiceLine.Item.SellersItemIdentification.ID();
            sellersItemIdentificationID.setAr(memoItem.getSku());
            sellersItemIdentificationID.setEn(memoItem.getSku());
            sellersItemIdentification.setID(sellersItemIdentificationID);

            Item.setSellersItemIdentification(sellersItemIdentification);
            Item.setClassifiedTaxCategory(ClassifiedTaxCategory);
            BigDecimal unitPriceExclTax = orderItem != null && orderItem.getOriginalPrice() !=null? orderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP): BigDecimal.ZERO;
            // Unit Price before discount
            InvoiceLine.Price Price = new InvoiceLine.Price();
            InvoiceLine.PriceAmount PriceAmount = new InvoiceLine.PriceAmount();
            PriceAmount.setCurrencyID(currency);
            PriceAmount.setValue(zatcaServiceImpl.parseNullStr(unitPriceExclTax));// unit price Exclusive Tax
            Price.setPriceAmount(PriceAmount);
            invoiceLine.setPrice(Price);

            // Discount
            List<InvoiceLine.AllowanceCharge> AllowanceCharges = new ArrayList<>();
            InvoiceLine.AllowanceCharge AllowanceCharge = new InvoiceLine.AllowanceCharge();
            InvoiceLine.Amount AllowanceAmount = new InvoiceLine.Amount();
            AllowanceAmount.setCurrencyID(currency);
            AllowanceAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getDiscountAmount()));
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
            LineExtensionAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getRowTotal()));
            invoiceLine.setLineExtensionAmount(LineExtensionAmount);

            // tax amount
            InvoiceLine.TaxTotal TaxTotal = new InvoiceLine.TaxTotal();
            InvoiceLine.TaxTotal.TaxAmount TaxAmount = new InvoiceLine.TaxTotal.TaxAmount();
            TaxAmount.setCurrencyID(currency);
            TaxAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getTaxAmount()));
            TaxTotal.setTaxAmount(TaxAmount);

            InvoiceLine.TaxTotal.RoundingAmount RoundingAmount = new InvoiceLine.TaxTotal.RoundingAmount();
            RoundingAmount.setCurrencyID(currency);
            RoundingAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getRowTotalInclTax()));// Inclusive Tax
            TaxTotal.setRoundingAmount(RoundingAmount);
            invoiceLine.setTaxTotal(TaxTotal);

            List<InvoiceLine.Item.AdditionalItemProperty> AdditionalItemProperty = new ArrayList<>();
            InvoiceLine.Item.AdditionalItemProperty ItemSubtotal = new InvoiceLine.Item.AdditionalItemProperty();
            ItemSubtotal.setName("product_subtotal");
            ItemSubtotal.setValue(zatcaServiceImpl.parseNullStr(
                    unitPriceExclTax.multiply(memoItem.getQty()).setScale(2, RoundingMode.HALF_UP)));
            AdditionalItemProperty.add(ItemSubtotal);

            Item.setAdditionalItemProperty(AdditionalItemProperty);

            invoiceLine.setItem(Item);

            invoiceLines.add(invoiceLine);

            lineCount ++;
        }
        return invoiceLines;
    }

    public List<InvoiceLine> returnRtoZatcaInvoiceLinesForSplitOrder(ZatcaConfig zatcaConfig, BigDecimal taxFactor, List<SalesCreditmemoItem> memoItems, List<SplitSalesOrderItem> splitSalesOrderItems, Map<String, ProductResponseBody> productsFromMulin, String currency){
        List<InvoiceLine> invoiceLines = new ArrayList<>();
        int lineCount = 1;
        for (SalesCreditmemoItem memoItem : memoItems) {
            SplitSalesOrderItem orderItem = splitSalesOrderItems.stream()
                    .filter(e -> e.getItemId().equals(memoItem.getOrderItemId())).findAny().orElse(null);
            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setID(String.valueOf(lineCount));

            InvoiceLine.Item.Name ItemName = new InvoiceLine.Item.Name();

            ItemName.setEn(memoItem.getName());
            ItemName.setAr(memoItem.getName());
            if (orderItem != null && orderItem.getParentSku() != null && productsFromMulin.containsKey(orderItem.getParentSku())) {
                ProductResponseBody productDetailsMulin = productsFromMulin.get(orderItem.getParentSku());
                if (null != productDetailsMulin.getAttributes().getName().getArabic()) {
                    ItemName.setAr(productDetailsMulin.getAttributes().getName().getArabic());
                    ItemName.setEn(productDetailsMulin.getAttributes().getName().getEnglish());
                }
            }

            InvoiceLine.Item.ClassifiedTaxCategory ClassifiedTaxCategory = new InvoiceLine.Item.ClassifiedTaxCategory();
            if(orderItem != null && orderItem.getTaxPercent() != null) {
                ClassifiedTaxCategory.setID(zatcaServiceImpl.getClassifiedTaxCategory(orderItem.getTaxPercent()));
                ClassifiedTaxCategory.setPercent(orderItem.getTaxPercent().setScale(2, RoundingMode.HALF_UP).toString());
            }

            InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme TaxScheme = new InvoiceLine.Item.ClassifiedTaxCategory.TaxScheme();

            TaxScheme.setID("VAT");
            ClassifiedTaxCategory.setTaxScheme(TaxScheme);
            InvoiceLine.InvoicedQuantity InvoicedQuantity = new InvoiceLine.InvoicedQuantity();
            InvoicedQuantity.setValue(memoItem.getQty().setScale(2, RoundingMode.HALF_UP).toString());
            invoiceLine.setInvoicedQuantity(InvoicedQuantity);

            InvoiceLine.Item Item = new InvoiceLine.Item();
            Item.setName(ItemName);

            InvoiceLine.Item.SellersItemIdentification sellersItemIdentification = new InvoiceLine.Item.SellersItemIdentification();
            InvoiceLine.Item.SellersItemIdentification.ID sellersItemIdentificationID = new InvoiceLine.Item.SellersItemIdentification.ID();
            sellersItemIdentificationID.setAr(memoItem.getSku());
            sellersItemIdentificationID.setEn(memoItem.getSku());
            sellersItemIdentification.setID(sellersItemIdentificationID);

            Item.setSellersItemIdentification(sellersItemIdentification);
            Item.setClassifiedTaxCategory(ClassifiedTaxCategory);
            BigDecimal unitPriceExclTax = orderItem != null && orderItem.getOriginalPrice() !=null? orderItem.getOriginalPrice().divide(taxFactor, 2, RoundingMode.HALF_UP): BigDecimal.ZERO;
            // Unit Price before discount
            InvoiceLine.Price Price = new InvoiceLine.Price();
            InvoiceLine.PriceAmount PriceAmount = new InvoiceLine.PriceAmount();
            PriceAmount.setCurrencyID(currency);
            PriceAmount.setValue(zatcaServiceImpl.parseNullStr(unitPriceExclTax));// unit price Exclusive Tax
            Price.setPriceAmount(PriceAmount);
            invoiceLine.setPrice(Price);

            // Discount
            List<InvoiceLine.AllowanceCharge> AllowanceCharges = new ArrayList<>();
            InvoiceLine.AllowanceCharge AllowanceCharge = new InvoiceLine.AllowanceCharge();
            InvoiceLine.Amount AllowanceAmount = new InvoiceLine.Amount();
            AllowanceAmount.setCurrencyID(currency);
            AllowanceAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getDiscountAmount()));
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
            LineExtensionAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getRowTotal()));
            invoiceLine.setLineExtensionAmount(LineExtensionAmount);

            // tax amount
            InvoiceLine.TaxTotal TaxTotal = new InvoiceLine.TaxTotal();
            InvoiceLine.TaxTotal.TaxAmount TaxAmount = new InvoiceLine.TaxTotal.TaxAmount();
            TaxAmount.setCurrencyID(currency);
            TaxAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getTaxAmount()));
            TaxTotal.setTaxAmount(TaxAmount);

            InvoiceLine.TaxTotal.RoundingAmount RoundingAmount = new InvoiceLine.TaxTotal.RoundingAmount();
            RoundingAmount.setCurrencyID(currency);
            RoundingAmount.setValue(zatcaServiceImpl.parseNullStr(memoItem.getRowTotalInclTax()));// Inclusive Tax
            TaxTotal.setRoundingAmount(RoundingAmount);
            invoiceLine.setTaxTotal(TaxTotal);

            List<InvoiceLine.Item.AdditionalItemProperty> AdditionalItemProperty = new ArrayList<>();
            InvoiceLine.Item.AdditionalItemProperty ItemSubtotal = new InvoiceLine.Item.AdditionalItemProperty();
            ItemSubtotal.setName("product_subtotal");
            ItemSubtotal.setValue(zatcaServiceImpl.parseNullStr(
                    unitPriceExclTax.multiply(memoItem.getQty()).setScale(2, RoundingMode.HALF_UP)));
            AdditionalItemProperty.add(ItemSubtotal);

            Item.setAdditionalItemProperty(AdditionalItemProperty);

            invoiceLine.setItem(Item);

            invoiceLines.add(invoiceLine);

            lineCount ++;
        }
        return invoiceLines;
    }

}
