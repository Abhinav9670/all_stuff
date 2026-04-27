const { shukranTransactionApi, generateRandomNumber, getDiscountAmount } = require('./action');
const orderObj = require('../helpers/order');
const safeJsonParse = require('../shukran/action');

exports.revertFailedOrderShukranTransaction = async incrementId => {
  try {
    if (incrementId.length === 6) {
      incrementId = `000${incrementId}`;
    }
    const data = await orderObj.getOrder({
      incrementId: incrementId,
      inclSubSales: true
    });

    const isShukranExists =
      data?.subSales?.shukran_linked &&
      data?.subSales?.shukran_card_number &&
      data?.subSales?.customer_profile_id;
    if (isShukranExists) {
      console.log("I'm in shukran Linked", incrementId);
      const {
        config: {
          shukranEnrollmentCommonCode = '',
          shukarnEnrollmentStoreCode = '',
          shukranEnrollmentConceptCode = '',
          shukranProgramCode = '',
          shukranItemTypeCode = '',
          shukranTransactionTypeCode = '',
          shukranCodItemName = '',
          shukranBasicItemName = '',
          environments = []
        }
      } = global;
      const stores = environments?.[0]?.stores;

      const transactionTypeCode = shukranTransactionTypeCode;
      const programCode = shukranProgramCode;
      const storeFind = stores.find(a => a.storeId == data.store_id) ?? [];
      const storeCode = storeFind?.['shukranStoreCode'];
      const invoiceTerritory = storeFind?.['invoiceTerritory'];

      const profileId = data.subSales.customer_profile_id;
      const fullStoreCode = `${shukranEnrollmentConceptCode}${shukarnEnrollmentStoreCode}${storeCode}`;
      const transactionNumber = `${shukranEnrollmentCommonCode}${data.increment_id}`;
      const revertTransactionNumber = `RXX${shukranEnrollmentCommonCode}${data.increment_id}`;

      const tenders = safeJsonParse(data?.subSales?.shukran_tenders, []);

      let finalTenders = [];
      tenders.forEach(t => {
        t.TenderAmount = -t.TenderAmount;
        finalTenders.push(t);
      });
      let taxFactor = 1;
      const crossBorderFlag = data?.subSales?.cross_border ? 'Y' : 'N';
      if (
        storeFind?.['taxPercentage'] &&
        Number(storeFind?.['taxPercentage']) > 0
      ) {
        taxFactor = 1 + Number(storeFind?.['taxPercentage']) / 100;
      }
      let orderNetPrice = 0;
      let isCod = false;
      if (data.cash_on_delivery_fee && data.cash_on_delivery_fee > 0) {
        orderNetPrice = parseFloat(
          (parseFloat(data.cash_on_delivery_fee) / taxFactor).toFixed(2)
        );
        isCod = true;
      }

      const dataToSend = {
        items: data.OrderItems,
        taxFactor: taxFactor,
        shukranItemTypeCode: shukranItemTypeCode,
        fullStoreCode: fullStoreCode,
        createdAt: data.created_at,
        transactionNumber: transactionNumber,
        shukranEnrollmentConceptCode: shukranEnrollmentConceptCode,
        invoiceTerritory: invoiceTerritory,
        orderNetPrice: orderNetPrice,
        shukranCodItemName,
        isCod,
        shukranBasicItemName,
        revertTransactionNumber
      };

      const transactionDetails = await createTransactionDetails(
        dataToSend,
        true
      );

      const payload = {
        ProfileId: profileId,
        TransactionTypeCode: 'RT',
        GrossAmount: null,
        TransactionNetTotal: -transactionDetails.finalOrderPrice,
        TransactionTotalTax: null,
        DiscountAmount: null,
        CardNumber: data.subSales.shukran_card_number,
        CurrencyCode: data.order_currency_code,
        TransactionDateTime: data.createdAt,
        StoreCode: fullStoreCode,
        TransactionNumber: revertTransactionNumber,
        ProgramCode: programCode,
        DeviceId: '',
        DeviceUserid: '',
        OriginalTransactionNumber: transactionNumber,
        OriginalTransactionDateTime: data.createdAt,
        OriginalStoreCode: fullStoreCode,
        ShippingAndHandling: null,
        TransactionDetails: transactionDetails.transactionData,
        Tenders: finalTenders,
        JsonExternalData: {
          VirtualCardIdentifier: '',
          VendorInvoiceNumber: '',
          InvoiceTotalQty: transactionDetails.totalQty,
          IsCoD: 'N',
          ProcessReturnFlag: '',
          IsCancel: '',
          IsOfflineTransaction: 'N', // need to check
          IsRetroTransaction: 'N', // need to check
          IsExternalPartnerTransaction: '',
          CrossBorderFlag: crossBorderFlag,
          PhoneNumber: '',
          LMSCartId: data?.subSales?.quote_id || null
        }
      };
      console.log('revert transaction payload ', JSON.stringify(payload));
      const result = await shukranTransactionApi({
        payload
      });

      return result;
    } else {
      console.log("I'm not in shukran Linked");
      return { message: 'Pr Updated Successfully' };
    }
  } catch (error) {
    global.logError('shukran PR log error', JSON.stringify(error));
    throw error;
  }
};

const createTransactionDetails = async data => {
  try {
    const transactionData = [];
    let lineNumber = 1;
    let totalQty = 0;
    let finalOrderPrice = data.orderNetPrice;

    for (const i of data.items) {
      if (
        i.product_type === 'simple' &&
        i.qty_ordered &&
        parseInt(i.qty_ordered) - parseInt(i.qty_canceled || 0) > 0
      ) {
        const itemQuantity =
          parseInt(i.qty_ordered) - parseInt(i.qty_canceled || 0);
        totalQty = totalQty + itemQuantity;
        const itemData = {
          DollarValueGross: 0,
          ShippingAndHandlingAmount: 0,
          ItemNumber: i.sku,
          ItemNumberTypeCode: data.shukranItemTypeCode,
          ItemDescription: i?.description || '',
          Quantity: itemQuantity,
          LineNumber: lineNumber,
          FulfillStoreCode: data.fullStoreCode,
          TransactionDateTime: data.createdAt,
          TransactionNumber: data.revertTransactionNumber,

          OriginalStoreCode: data.fullStoreCode,
          OriginalTransactionDateTime: data.createdAt,
          OriginalTransactionNumber: data.transactionNumber,
          Uom: null,
          JsonExternalData: {
            lmsmultiplier: null,
            PromoCode: null,
            LMSMultiplier: null,
            ItemDescription: i?.description || null,
            ItemDescription_AR: i?.name || null,
            SaleFlag: i.on_sale ? 'Y' : 'N',
            ConceptCode: data.shukranEnrollmentConceptCode,
            DepartmentCode: i?.shukran_l4_category ?? '',
            ProductName: data.shukranBasicItemName,
            IsBeautyBay: 'N',
            InvoiceTerritory: data.invoiceTerritory
          }
        };
        const taxFactor = data.taxFactor;
        const itemSubTotal =
          (parseFloat(i.original_price) / taxFactor) * parseInt(i.qty_ordered);
        const itemDiscount1 =
          ((parseFloat(i.original_price) - parseFloat(i.price_incl_tax)) /
            taxFactor) *
          parseInt(i.qty_ordered);
        const discountAmount = await getDiscountAmount(
          i.order_id,
          i.parent_item_id
        );

        const itemDiscount2 =
          (parseFloat(i.discount_amount) - parseFloat(discountAmount)) /
          taxFactor;
        const totalDiscount = itemDiscount1 + itemDiscount2;
        const dollarValueNet = parseFloat(
          (
            ((itemSubTotal - totalDiscount) * itemQuantity) /
            parseInt(i.qty_ordered)
          ).toFixed(2)
        );

        itemData.DollarValueNet = -dollarValueNet;
        finalOrderPrice = finalOrderPrice + dollarValueNet;
        itemData.TaxAmount = 0;
        itemData.DiscountAmount = 0;
        if (parseFloat(dollarValueNet) > 0) {
          transactionData.push(itemData);
          lineNumber++;
        }
      }
    }
    if (data.isCod) {
      const randomNumber = await generateRandomNumber();
      transactionData.push({
        DollarValueGross: 0,
        DollarValueNet: -parseFloat(data.orderNetPrice),
        TaxAmount: 0,
        DiscountAmount: 0,
        ShippingAndHandlingAmount: 0,
        ItemNumber: randomNumber,
        ItemNumberTypeCode: data.shukranItemTypeCode,
        ItemDescription: '',
        Quantity: 1,
        LineNumber: lineNumber,
        FulfillStoreCode: data.fullStoreCode,
        TransactionDateTime: data.createdAt,
        TransactionNumber: data.revertTransactionNumber,
        OriginalStoreCode: data.fullStoreCode,
        OriginalTransactionDateTime: data.createdAt,
        OriginalTransactionNumber: data.TransactionNumber,
        Uom: null,
        JsonExternalData: {
          lmsmultiplier: null,
          PromoCode: null,
          LMSMultiplier: null,
          ItemDescription: 'Cash On Delivery',
          ItemDescription_AR: null,
          SaleFlag: 'N',
          ConceptCode: data.shukranEnrollmentConceptCode,
          DepartmentCode: randomNumber,
          ProductName: data.shukranCodItemName,
          IsBeautyBay: 'N',
          InvoiceTerritory: data.invoiceTerritory
        }
      });
    }

    return {
      transactionData: transactionData,
      finalOrderPrice: parseFloat(finalOrderPrice.toFixed(2)),
      totalQty
    };
  } catch (err) {
    throw new Error(err.message);
  }
};
