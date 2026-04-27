package org.styli.services.order;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.sales.SalesCreditmemoItem;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesInvoiceItem;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SubSalesOrder;

public class OrderObjects {
	static SalesOrder salesOrder;
	
	public static SalesCreditmemo getSalesCreditmemo() {
		SalesCreditmemo salesCreditMemo = new SalesCreditmemo();
		salesCreditMemo.setIncrementId("70003768");
		salesCreditMemo.setOrderId(1);
		salesCreditMemo.setEntityId(1);
		salesCreditMemo.setEasValueInCurrency(BigDecimal.valueOf(23.44));
		salesCreditMemo.setGrandTotal(BigDecimal.valueOf(98.88));
		salesCreditMemo.setAmstorecreditAmount(BigDecimal.valueOf(23.99));
		return salesCreditMemo;
	}

	public static SalesCreditmemoItem getSalesCreditmemoItem() {
		SalesCreditmemoItem salesCreditMemoItem = new SalesCreditmemoItem();
		salesCreditMemoItem.setParentId(1);
		salesCreditMemoItem.setQty(BigDecimal.valueOf(1));
		salesCreditMemoItem.setName("Name");
		salesCreditMemoItem.setOrderItemId(2);
		salesCreditMemoItem.setDiscountAmount(BigDecimal.valueOf(12.99));
		salesCreditMemoItem.setTaxAmount(BigDecimal.valueOf(11.11));
		salesCreditMemoItem.setRowTotalInclTax(BigDecimal.valueOf(11.98));
		salesCreditMemoItem.setEntityId(1);
		return salesCreditMemoItem;
	}

	public static SalesOrder getSalesOrder() {
		salesOrder = new SalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setIncrementId("1");
		salesOrder.setCustomerIsGuest(2);
		salesOrder.setEntityId(1);
		salesOrder.setStoreId(1);
		salesOrder.setStatus("packed");
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(Integer.valueOf(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
		LocalDateTime currentTime = LocalDateTime.now();
		salesOrder.setCreatedAt(Timestamp.valueOf(currentTime));
		salesOrder.setWmsStatus(1);
		salesOrder.setExtOrderId("0");

		salesOrder.setSalesOrderItem(getSalesOrderItem());

		Set<SalesInvoice> invoices = new HashSet<>();
		invoices.add(getSalesInvoice());
		salesOrder.setSalesInvoices(invoices);

		Set<SalesOrderPayment> sop = new HashSet<>();
		sop.add(getSalesOrderPayment());
		salesOrder.setSalesOrderPayment(sop);

		salesOrder.setSubSalesOrder(getSubSalesOrder());

		Set<SalesShipmentTrack> salesShipmentTrack = new HashSet<>();
		salesShipmentTrack.add(getSalesShipmentTrack());
		salesOrder.setSalesShipmentTrack(salesShipmentTrack);

		return salesOrder;
	}

	public static SalesOrderPayment getSalesOrderPayment() {
		SalesOrderPayment pay = new SalesOrderPayment();
		pay.setMethod("payfort_fort_cc");
		return pay;
	}

	public static SalesShipmentTrack getSalesShipmentTrack() {
		SalesShipmentTrack track = new SalesShipmentTrack();
		track.setTrackNumber("1");
		return track;
	}

	public static SubSalesOrder getSubSalesOrder() {
		SubSalesOrder subSalesOrder = new SubSalesOrder();
		subSalesOrder.setDonationAmount(BigDecimal.valueOf(10.0));
		return subSalesOrder;
	}

	public static Set<SalesOrderItem> getSalesOrderItem() {
		SalesOrderItem item = new SalesOrderItem();
		item.setItemId(1234);
		item.setSku("01");
		item.setItemId(1);
		item.setProductType("prepaid");
		item.setPriceInclTax(new BigDecimal(10));
		item.setQtyOrdered(new BigDecimal(2));
		item.setQtyCanceled(new BigDecimal(1));
		item.setDiscountAmount(new BigDecimal(5));
		item.setRowTotalInclTax(new BigDecimal(10));
		item.setParentOrderItem(item);

		SalesOrderItem configitem = new SalesOrderItem();
		configitem.setItemId(123);
		configitem.setSku("01");
		configitem.setItemId(2);
		configitem.setProductType("configurable");
		configitem.setPriceInclTax(new BigDecimal(10));
		configitem.setQtyOrdered(new BigDecimal(2));
		configitem.setQtyCanceled(new BigDecimal(1));
		configitem.setDiscountAmount(new BigDecimal(5));
		configitem.setRowTotalInclTax(new BigDecimal(10));
		configitem.setParentOrderItem(configitem);

		Set<SalesOrderItem> orderItems = new HashSet<>();
		orderItems.add(item);
		orderItems.add(configitem);
		return orderItems;
	}

	public static SalesInvoice getSalesInvoice() {
		SalesInvoice invoice = new SalesInvoice();
		invoice.setIncrementId("234234");
		invoice.setShippingAmount(BigDecimal.valueOf(5.99));
		invoice.setCashOnDeliveryFee(BigDecimal.valueOf(9.0));
		invoice.setEasValueInBaseCurrency(BigDecimal.valueOf(12.33));
		invoice.setEasValueInCurrency(BigDecimal.valueOf(12.33));
		invoice.setAmstorecreditAmount(BigDecimal.valueOf(23.43));
		invoice.setBaseGrandTotal(BigDecimal.valueOf(54.67));
		invoice.setZatcaStatus("REPORTED");

		Set<SalesInvoiceItem> salesInvoiceItems = new HashSet<>();
		SalesInvoiceItem salesInvoiceItem = new SalesInvoiceItem();
		salesInvoiceItem.setOrderItemId(2);
		salesInvoiceItem.setDiscountAmount(BigDecimal.valueOf(56.98));
		salesInvoiceItem.setTaxAmount(BigDecimal.valueOf(2.98));
		salesInvoiceItem.setQuantity(BigDecimal.valueOf(2));
		salesInvoiceItem.setRowTotalInclTax(BigDecimal.valueOf(98.8));
		salesInvoiceItem.setName("Unit Test");
		salesInvoiceItems.add(salesInvoiceItem);
		invoice.setSalesInvoiceItem(salesInvoiceItems);

		invoice.setSalesOrder(salesOrder);
		return invoice;
	}
}
