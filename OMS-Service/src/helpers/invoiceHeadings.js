/* eslint-disable max-lines-per-function */

exports.getStoreWiseHeadings = data => {
  const headerData = {
    unitPrice: {
      en: 'Unit Price',
      ar: "سعر الوحده"
    },
    subTotal: {
      en: 'Sub Total',
      ar: "المجموع الفرعي"
    },
    rowTotal: {
      en: 'Row Total',
      ar: "المجموع الصافي"
    },
    invoiceTotal: {
      en: 'Sub Total',
      ar: "المجموع الفرعي"
    },
    invoiceDiscount: {
      en: 'Discount',
      ar: "تخفيض"
    },
    invoiceHeading: {
      en: 'Tax Invoice',
      ar: "فاتورة ضريبية"
    },
    shippingAmount: {
      en: 'Shipping & Handling',
      ar: "الشحن والتسليم"
    },
    codFee: {
      en: 'COD Charges',
      ar: "تكلفة الدفع عند الإستلام"
    },
    currencyText: {
      en: `(Value in ${data?.totals?.currency})`,
      ar: `(${data?.totals?.currency} القيمة بال)`
    },
    itemCode: {
      en: 'Item code',
      ar: "رمز المنتج"
    },
    productName: {
      en: 'Product name',
      ar: "اسم المنتج"
    },
    qty: {
      en: 'Qty',
      ar: "الكمية"
    },
    unitPriceExclVat: {
      en: 'Unit Price (excl VAT)',
      ar: "سعر الوحدة ( بدون  قيمة الضريبة المضافه )"
    },
    subtotalExclVat: {
      en: 'Sub Total (excl VAT)',
      ar: "المجموع الفرعي ( بدون قيمة الضريبة المضافه )"
    },
    discountExclVat: {
      en: 'Discount (excl VAT)',
      ar: "التخفيض ( بدون قيمة الضريبه المضافه )"
    },
    taxableAmount: {
      en: 'Total Taxable Amount',
      ar: "مجموع المبلغ الخاضع للضريبة"
    },
    vat: {
      en: 'VAT',
      ar: " الضريبه المضافه"
    },
    vatRate: {
      en: 'VAT Rate',
      ar: "قيمة الضريبة"
    },
    vatAmount: {
      en: 'VAT Amount',
      ar: "قيمة الضريبه المضافه"
    },
    totalPayable: {
      en: 'Total payable (Inc VAT)',
      ar: "مجموع المبلغ المستحق للدفع ( شاملاً قيمة الضريبة المضافه )"
    },
    settlement: {
      en: 'Settlement',
      ar: "التسوية"
    },
    totalExclVat: {
      en: 'Total excl vat',
      ar: "المجموع غير شاملاً قيمة الضريبة المضافه"
    },
    payableRounding: {
      en: 'Payable Rounding Amount',
      ar: "مجموع المبلغ المستحق بعد التقريب"
    },
    totalPayable: {
      en: 'Total payable',
      ar: "مجموع المبلغ المستحق"
    },
    giftVoucher: {
      en: 'Gift Voucher',
      ar: "قسيمة شراء"
    },
    styliCoins: {
      en: 'Styli Cash',
      ar: "نقد ستايلي"
    },
    styliCredit: {
      en: 'STYLI Credits',
      ar: "رصيد ستايلي"
    },
    totalNetPayable: {
      en: 'Total Payable amount (Incl VAT)',
      ar: "مجموع المبلغ المستحق ( شاملا ضريبة القيمة المضافه )"
    },
    importFee: {
      en: 'Import Fee',
      ar: "رسوم التوريد"
    },
    grandTotalExclVAT: {
      en: 'Grand Total(Excl VAT)',
      ar: "المجموع النهائي ( غير شاملاً قيمة الضريبة المضافه )"
    },
    codFeeExclVat: {
      en: 'Cash on delivery Fee',
      ar: "رسوم الدفع عند الإستلام"
    },
    shippingAmountExclVat: {
      en: 'Shipping & Handling',
      ar: "قيمة الشحن والتسليم"
    },
    refundedAmount: {
      en: 'Total Refundable amount (Incl VAT)',
      ar: "مجموع المبلغ المسترد ( شاملا ضريبة القيمة المضافة )"
    },
    exchangeRate: {
      en: 'Exchange Rate',
      ar: "سعر الصرف"
    },
    refundReason: {
      en: "Reason for Issuing Note: Refund",
      ar: "سبب إصدار الملاحظة: استرداد المبلغ",
    },
    supplyDate: {
      en: "Supply Date",
      ar: "تاريخ التوريد",
    },
    originalSupplyDate: {
      en: "Original Supply date",
      ar: "تاريخ التوريد الأصلي",
    }
  };

  try {
    const { storeId } = data;
    headerData.isUaeOrKsa = ['1', '3', '7', '11'].includes(storeId);
    headerData.isUae = ['7', '11'].includes(storeId);
    headerData.isKsa = ['1', '3'].includes(storeId);
    headerData.isNotKsa = !['1', '3'].includes(storeId);
    if (headerData.isKsa) {
      headerData.invoiceHeading = {
        en: 'Simplified Tax Invoice',
        ar: 'فاتورة ضريبية مبسطة'
      };
      headerData.unitPrice = {
        en: 'Unit Price (Inc VAT)',
        ar: 'سعر الوحدة (شامل ضريبة القيمة المضافة)'
      };
      headerData.rowTotal = {
        en: 'Row Total (Inc VAT)',
        ar: 'إجمالي الصف (شامل ضريبة القيمة المضافة)'
      };
      headerData.subTotal = {
        en: 'Sub Total (Inc VAT)',
        ar: 'المجموع الفرعي (شامل ضريبة القيمة المضافة)'
      };
      headerData.invoiceTotal = {
        en: 'Subtotal (Inc VAT)',
        ar: 'المجموع الفرعي (شامل ضريبة القيمة المضافة)'
      };
      headerData.invoiceDiscount = {
        en: 'Discount',
        ar: 'تخفيض'
      };
      headerData.shippingAmount = {
        en: 'Shipping & Handling (Inc VAT)',
        ar: 'الشحن و التوصيل'
      };
      headerData.codFee = {
        en: 'COD Fee (Inc VAT)',
        ar: 'رسوم الدفع عند الاستلام (شاملة ضريبة القيمة المضافة)'
      };
    }

    if (headerData.isKsa) {
      headerData.currencyText.ar = '(القيمة بالريال السعودي)';
    }
    if (headerData.isUae) {
      headerData.currencyText.ar = '(القيمة بالدرهم الإماراتي)';
    }
  } catch (e) {
    console.error('Error in creating headers Invoice details. ', e);
  }
  return headerData;
};
