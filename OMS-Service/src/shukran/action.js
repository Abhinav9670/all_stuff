const { client } = require('../config/redis');
const orderObj = require('../helpers/order');
const rmaObj = require('../helpers/rma');
const { updateShukranLedger } = require('../utils/easApi');
const { getStoreConfigs } = require('../utils/config');
const axios = require('axios');
const crypto = require('crypto');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];
const { SubSalesOrder } = require('../models/seqModels/index');

const logPrefix = '[shukranSplitOrderHandling]';

exports.getAccessToken = async () => {
  const cacheName = 'epsilon-bucket';
  const key = 'epsilon-token';
  const redisKey = `${cacheName}:${key}`;
  try {
    const value = await client.get(redisKey);
    global.logInfo('shukran-redis ', value, JSON.parse(value));
    return JSON.parse(value)?.['access_token'] || '';
  } catch (e) {
    global.logError('Error In Get Access Token', e);
    return '';
  }
};

const safeJsonParse = (value, defaultValue = null) => {
  // Already an object/array, return as-is
  if (typeof value === 'object' && value !== null) {
    return value;
  }
  
  // String, try to parse
  if (typeof value === 'string') {
    try {
      return JSON.parse(value);
    } catch (e) {
      global.logError('JSON parse error:', e, value);
      return defaultValue;
    }
  }
  
  // Null, undefined, or other types
  return defaultValue;
};

exports.shukranTransactionApi = async ({ payload }) => {
  const {
    config: { shukranTransactionRTPRURL = '' }
  } = global;

  try {
    const domainURL = shukranTransactionRTPRURL;

    const response = await axios.post(domainURL, payload, {
      headers: { 'authorization-token': internalAuthToken }
    });
    global.logInfo(
      `shukran transaction api response, ${payload.TransactionNumber},  ${
        response.data ? JSON.stringify(response.data) : ''
      }, ${
        payload.OriginalTransactionNumber
          ? payload.OriginalTransactionNumber
          : ' '
      }`
    );
    if (
      response?.data?.response?.JsonExternalData?.TotalPointsEarned &&
      !payload.OriginalTransactionNumber
    ) {
      await rmaObj.saveShukranEarnedPointsInDb(
        payload.TransactionNumber,
        response?.data?.response?.JsonExternalData?.TotalPointsEarned,
        payload.TransactionNetTotal
      );
      await rmaObj.saveShukranEarnedPointsInOrderHistory(
        payload.TransactionNumber,
        response?.data?.response?.JsonExternalData?.TotalPointsEarned
      );
      await updateShukranLedger(
        payload.TransactionNumber,
        response?.data?.response?.JsonExternalData?.TotalPointsEarned,
        false,
        'Earned Skukran Points On Pr Call'
      );
    }

    return response;
  } catch (error) {
    await rmaObj.saveShukranPrTimingInDb(payload.TransactionNumber);
    if (error.response) {
      global.logError(error.message, { msg: 'error in PR call api' });
      throw error.response.data;
    }
    throw error;
  }
};

const handleShukranPaymentValidation = data => {
  return (
    data.paymentInformation &&
    data.paymentInformation.length > 0 &&
    data.paymentInformation.findIndex(p => p.method === 'shukran_payment') > -1
  ) 
};

const calculateOrderNetPrice = (data, taxFactor) => {
  let orderNetPrice = 0;
  let isCod = false;
  if (data.cash_on_delivery_fee && data.cash_on_delivery_fee > 0) {
    orderNetPrice = parseFloat(
      (parseFloat(data.cash_on_delivery_fee) / taxFactor).toFixed(2)
    );
    isCod = true;
  }
  return { orderNetPrice, isCod };
};

const getTaxFactor = storeFind => {
  let taxFactor = 1;
  if (
    storeFind?.['taxPercentage'] &&
    Number(storeFind?.['taxPercentage']) > 0
  ) {
    taxFactor = 1 + Number(storeFind?.['taxPercentage']) / 100;
  }
  return taxFactor;
};

exports.shukranTransactionCreate = async ({ incrementId, excludedOrderItemIds = [] }) => {
  try {
    const { baseConfig = {} } = global;
    const shukranOnShipmentLevel = baseConfig?.shukranOnShipmentLevel ?? false;
    
    console.log(`${logPrefix} shukranTransactionCreate: Starting PR call`, {
      incrementId,
      excludedOrderItemIdsCount: excludedOrderItemIds?.length || 0,
      shukranOnShipmentLevel,
      excludedOrderItemIds: excludedOrderItemIds
    });
    
    const data = await orderObj.getOrder({
      incrementId,
      inclSubSales: true
    });
    global.logInfo(
      'order data for shukran transaction creation ',
      JSON.stringify(data)
    );

    const isShukranExists =
      data?.subSales?.shukran_linked &&
      data?.subSales?.shukran_card_number &&
      data?.subSales?.customer_profile_id;
    if (isShukranExists) {
      global.logInfo("I'm in shukran Linked", incrementId);
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

      global.logInfo(
        `shukran transaction create consul data ,${incrementId},  ${shukranEnrollmentCommonCode}, ,${shukarnEnrollmentStoreCode}, ${shukranEnrollmentConceptCode}, ${shukranProgramCode},  ${shukranItemTypeCode}, ${shukranTransactionTypeCode}, ${shukranCodItemName}, ${shukranBasicItemName}, ${JSON.stringify(
          stores
        )}`
      );

      const transactionTypeCode = shukranTransactionTypeCode;
      const programCode = shukranProgramCode;
      const storeFind = stores.find(a => a.storeId == data.store_id) ?? [];
      const storeCode = storeFind?.['shukranStoreCode'];
      const invoiceTerritory = storeFind?.['invoiceTerritory'];

      const profileId = data.subSales.customer_profile_id;
      const fullStoreCode = `${shukranEnrollmentConceptCode}${shukarnEnrollmentStoreCode}${storeCode}`;
      const transactionNumber = `${shukranEnrollmentCommonCode}${data.increment_id}`;
      const tenders = safeJsonParse(data?.subSales?.shukran_tenders, []);

      const taxFactor = getTaxFactor(storeFind);
      const { orderNetPrice, isCod } = calculateOrderNetPrice(data, taxFactor);
      const crossBorderFlag = data?.subSales?.cross_border ? 'Y' : 'N';

      // Only exclude items if feature flag is enabled
      const itemsToExclude = shukranOnShipmentLevel ? excludedOrderItemIds : [];
      
      console.log(`${logPrefix} shukranTransactionCreate: Feature flag check for item exclusion`, {
        incrementId,
        shukranOnShipmentLevel,
        excludedOrderItemIdsProvided: excludedOrderItemIds?.length || 0,
        itemsToExcludeCount: itemsToExclude.length,
        itemsToExclude: itemsToExclude
      });

      // For split orders, fetch items from split_sales_order_item
      // Check if this is a parent order with split shipments
      let items = data?.OrderItems ?? [];
      
      // Fallback for split orders: combine simpleItems and configItems if OrderItems is empty
      if (!items || items.length === 0) {
        const simpleItems = data?.simpleItems || [];
        const configItems = data?.configItems || [];
        items = [...simpleItems, ...configItems];
        console.log(`${logPrefix} shukranTransactionCreate: Using fallback items from simpleItems + configItems incrementId: ${incrementId}`, JSON.stringify({
          incrementId,
          simpleItemsCount: simpleItems.length,
          configItemsCount: configItems.length,
          totalItemsCount: items.length,
          hasOrderItems: !!data?.OrderItems,
          orderItemsCount: data?.OrderItems?.length || 0
        }, null, 2));
      }
      
      // For parent orders with split shipments, fetch items from split_sales_order_item
      // where order_id = parent_order.entity_id (e.g., 11704265)
      if (shukranOnShipmentLevel && data?.entity_id) {
        const { isSplitOrderPattern } = require('../utils/splitOrderUtils');
        const isSplitOrder = incrementId && isSplitOrderPattern(incrementId);
        
        // If this is NOT a split order pattern, it might be a parent order with split shipments
        if (!isSplitOrder) {
          console.log(`${logPrefix} shukranTransactionCreate: Checking if parent order has split shipments incrementId: ${incrementId}`, JSON.stringify({
            incrementId,
            entity_id: data.entity_id
          }, null, 2));
          
          try {
            const { QueryTypes } = require('sequelize');
            const Models = require('../models/seqModels/index');
            
            // Check if there are split shipments for this order
            const splitShipmentsQuery = `
              SELECT DISTINCT entity_id
              FROM split_sales_order
              WHERE order_id = :orderId
              LIMIT 1
            `;
            
            const splitShipmentsResult = await Models.sequelize.query(splitShipmentsQuery, {
              replacements: { orderId: data.entity_id },
              type: QueryTypes.SELECT
            });
            
            if (splitShipmentsResult && splitShipmentsResult.length > 0) {
              console.log(`${logPrefix} shukranTransactionCreate: Parent order has split shipments, fetching items from split_sales_order_item incrementId: ${incrementId}`, JSON.stringify({
                incrementId,
                entity_id: data.entity_id,
                order_id: data.entity_id
              }, null, 2));
              
              // Fetch items from split_sales_order_item where order_id = parent_order.entity_id
              // Aggregate quantities by sales_order_item_id to handle same product in multiple shipments
              const splitOrderItemsQuery = `
                SELECT 
                  sales_order_item_id,
                  MAX(item_id) as item_id,
                  MAX(order_id) as order_id,
                  MAX(split_order_id) as split_order_id,
                  MAX(parent_item_id) as parent_item_id,
                  MAX(sku) as sku,
                  MAX(name) as name,
                  MAX(description) as description,
                  product_type,
                  SUM(qty_ordered) as qty_ordered,
                  SUM(qty_canceled) as qty_canceled,
                  MAX(original_price) as original_price,
                  MAX(price_incl_tax) as price_incl_tax,
                  MAX(discount_amount) as discount_amount,
                  MAX(shukran_l4_category) as shukran_l4_category,
                  MAX(on_sale) as on_sale
                FROM split_sales_order_item
                WHERE order_id = :orderId
                  AND product_type = 'simple'
                  AND sales_order_item_id IS NOT NULL
                GROUP BY sales_order_item_id, product_type
                ORDER BY sales_order_item_id
              `;
              
              const splitOrderItemsResult = await Models.sequelize.query(splitOrderItemsQuery, {
                replacements: { orderId: data.entity_id },
                type: QueryTypes.SELECT
              });
              
              if (splitOrderItemsResult && splitOrderItemsResult.length > 0) {
                console.log(`${logPrefix} shukranTransactionCreate: Fetched and aggregated items from split_sales_order_item incrementId: ${incrementId}`, JSON.stringify({
                  incrementId,
                  order_id: data.entity_id,
                  itemsCount: splitOrderItemsResult.length,
                  items: splitOrderItemsResult.map(i => ({
                    item_id: i.item_id,
                    sales_order_item_id: i.sales_order_item_id,
                    sku: i.sku,
                    qty_ordered: i.qty_ordered,
                    qty_canceled: i.qty_canceled,
                    product_type: i.product_type
                  }))
                }, null, 2));
                
                // Use aggregated items from split_sales_order_item instead of OrderItems
                items = splitOrderItemsResult;
              } else {
                console.log(`${logPrefix} shukranTransactionCreate: No items found in split_sales_order_item, using original items incrementId: ${incrementId}`, JSON.stringify({
                  incrementId,
                  order_id: data.entity_id,
                  originalItemsCount: items.length
                }, null, 2));
              }
            }
          } catch (error) {
            console.error(`${logPrefix} shukranTransactionCreate: Error fetching items from split_sales_order_item incrementId: ${incrementId}`, JSON.stringify({
              incrementId,
              error: error.message,
              stack: error.stack
            }, null, 2));
            global.logError('Error in shukranTransactionCreate fetching split order items', error);
            // Continue with original items if error occurs
          }
        }
      }

      const dataToSend = {
        items: items,
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
        excludedOrderItemIds: itemsToExclude,
        shukranOnShipmentLevel: shukranOnShipmentLevel
      };
      
      console.log(`${logPrefix} shukranTransactionCreate: Passing data to createTransactionDetails incrementId: ${incrementId}`, JSON.stringify({
        incrementId,
        totalItems: items.length,
        originalOrderItemsCount: data.OrderItems?.length || 0,
        simpleItemsCount: data.simpleItems?.length || 0,
        configItemsCount: data.configItems?.length || 0,
        excludedOrderItemIds: itemsToExclude,
        shukranOnShipmentLevel,
        itemsSource: items.length > 0 ? (items[0].split_order_id !== undefined || items[0].order_id === data.entity_id ? 'split_sales_order_item' : 'OrderItems/simpleItems+configItems') : 'none',
        sampleItem: items.length > 0 ? {
          item_id: items[0].item_id,
          sales_order_item_id: items[0].sales_order_item_id,
          sku: items[0].sku,
          qty_ordered: items[0].qty_ordered,
          qty_canceled: items[0].qty_canceled
        } : null
      }, null, 2));

      const transactionDetails = await createTransactionDetails(dataToSend);

      const payload = {
        ProfileId: profileId,
        TransactionTypeCode: transactionTypeCode,
        GrossAmount: null,
        TransactionNetTotal: transactionDetails.finalOrderPrice,
        TransactionTotalTax: null,
        DiscountAmount: null,
        CardNumber: data.subSales.shukran_card_number,
        CurrencyCode: data.order_currency_code,
        TransactionDateTime: data.createdAt,
        StoreCode: fullStoreCode,
        TransactionNumber: transactionNumber,
        ProgramCode: programCode,
        DeviceId: '',
        DeviceUserid: '',
        OriginalTransactionNumber: '',
        OriginalTransactionDateTime: '',
        OriginalStoreCode: fullStoreCode,
        ShippingAndHandling: null,
        TransactionDetails: transactionDetails.transactionData,
        Tenders: tenders,
        JsonExternalData: {
          VirtualCardIdentifier: '',
          VendorInvoiceNumber: '',
          InvoiceTotalQty: transactionDetails.totalQty,
          IsCoD: 'N',
          ProcessReturnFlag: '',
          IsCancel: '',
          IsOfflineTransaction: 'N',
          IsRetroTransaction: 'N',
          IsExternalPartnerTransaction: '',
          CrossBorderFlag: crossBorderFlag,
          PhoneNumber: '',
          LMSCartId: data?.subSales?.quote_id || null
        }
      };

      global.logInfo(
        `Shukran PR api payload: ${incrementId},  ${JSON.stringify(payload)}`
      );

      //const isShukranPayment = handleShukranPaymentValidation(data);
      if (
        !payload.TransactionNetTotal ||
        payload.TransactionNetTotal === 0 ||
        payload.TransactionNetTotal === '0')
      {
        await rmaObj.saveShukranPrSuccessfulInDb(payload.TransactionNumber);
        throw new Error('Invalid transaction net total');
      }

      const result = await this.shukranTransactionApi({
        payload
      });
      // API returns axios response: result.data = body; on 4xx/5xx we throw, so result is only set on 2xx
      const apiError = result?.data?.error || result?.error;
      const alreadyExistsMessage = apiError?.Message;
      const isAlreadyExists =
        alreadyExistsMessage &&
        [
          'Provided Transaction Number already exists in the system',
          'Transaction already exists.'
        ].includes(alreadyExistsMessage);

      if (isAlreadyExists) {
        global.logInfo(
          `in transaction already exists in the system condition , ${incrementId}, ${alreadyExistsMessage}`
        );
        await orderObj.updateSubsalesOrderData(payload, data);
      } else if (!apiError) {
        // PR call succeeded (2xx, no error in body) – persist shukran_pr_successful
        await orderObj.updateSubsalesOrderData(payload, data);
      }
      return result;
    } else {
      console.log("I'm not in shukran Linked");
      return { message: 'Pr Updated Successfully' };
    }
  } catch (error) {
    global.logError(
      `shukran PR log error: ${incrementId},  ${
        error.message ? JSON.stringify(error.message) : ''
      },  ${error}`
    );
    throw error;
  }
};

exports.getCustomerShukranProfile = async (mobileNumber, storeId) => {
  try {
    if (!mobileNumber) {
      throw new Error('Invalid parameters');
    }
    if (!storeId || storeId === 0) {
      storeId = 1;
    }
    global.logInfo(
      `getCustomerShukranProfile data recieved, ${mobileNumber}, storeId, ${storeId}`
    );
    const token = await this.getAccessToken();
    const {
      config: { shukranProgramCode = '', shukranSourceApplication = '' }
    } = global;
    const storeData = await getStoreConfigs({
      key: 'shukranCurrencyCode',
      storeId: storeId
    });

    global.logInfo(
      `getCustomerShukranProfile data recieved, ${
        storeData ? JSON.stringify(storeData) : ''
      }`
    );
    if (storeData && storeData.length > 0) {
      const dataToHit = {
        ProfileId: '',
        FieldName: 'PhoneNumber',
        LookupValue: mobileNumber.replace(/[ +]/g, ''),
        Country: storeData[0].shukranCurrencyCode
      };

      global.logInfo(
        `getCustomerShukranProfile data to hit, ${JSON.stringify(dataToHit)}`
      );

      const headerData = {
        'Program-Code': shukranProgramCode,
        Authorization: `OAuth ${token}`,
        'Content-Type': 'application/json',
        'Accept-Language': 'en-US',
        'Source-Application': shukranSourceApplication
      };
      global.logInfo(
        `getCustomerShukranProfile data to header data, ${JSON.stringify(
          headerData
        )}`
      );

      const result = await axios.post(
        `${process.env.PROXY_HOST}/api/v1/infrastructure/scripts/GetProfileDetailsWithTier_V2/invoke`,
        dataToHit,
        {
          headers: headerData
        }
      );
      if (result?.data?.JsonExternalData?.AvailablePoints) {
        return result.data.JsonExternalData.AvailablePoints;
      } else {
        return 0;
      }
    } else {
      return 0;
    }
  } catch (e) {
    global.logInfo(
      `getCustomerShukranProfile error, ${
        e.message ? JSON.stringify(e.message) : ''
      }, ${e}`
    );
    return 0;
  }
};

const createTransactionDetails = async data => {
  try {
    const transactionData = [];
    let lineNumber = 1;
    let totalQty = 0;
    let finalOrderPrice = data.orderNetPrice;
    const shukranOnShipmentLevel = data.shukranOnShipmentLevel ?? false;
    const excludedOrderItemIds = shukranOnShipmentLevel ? (data.excludedOrderItemIds || []) : [];
    
      console.log(`${logPrefix} createTransactionDetails: Processing items`, {
        totalItems: data.items?.length || 0,
        shukranOnShipmentLevel,
        excludedOrderItemIdsCount: excludedOrderItemIds.length,
        excludedOrderItemIds: excludedOrderItemIds,
        allItemIds: data.items?.map(i => ({ item_id: i.item_id, item_id_type: typeof i.item_id, sku: i.sku })) || []
      });

    let excludedItemsCount = 0;
    // Convert excludedOrderItemIds to numbers for proper comparison (item_id is INTEGER in DB)
    const excludedOrderItemIdsAsNumbers = excludedOrderItemIds.map(id => Number(id)).filter(id => !isNaN(id));
    
    // Check if items have sales_order_item_id field (indicates split order items)
    // For split orders: SplitSalesOrderItem has sales_order_item_id that links to original sales_order_item.item_id
    // For regular orders: OrderItem has item_id directly (which is the sales_order_item.item_id)
    const hasSalesOrderItemId = data.items?.some(item => item.sales_order_item_id !== undefined && item.sales_order_item_id !== null) || false;
    
    console.log(`${logPrefix} createTransactionDetails: Excluded order_item_ids (converted to numbers)`, {
      excludedOrderItemIds: excludedOrderItemIds,
      excludedOrderItemIdsAsNumbers: excludedOrderItemIdsAsNumbers,
      excludedCount: excludedOrderItemIdsAsNumbers.length,
      hasSalesOrderItemId: hasSalesOrderItemId,
      sampleItem: data.items?.[0] ? {
        item_id: data.items[0].item_id,
        sales_order_item_id: data.items[0].sales_order_item_id,
        sku: data.items[0].sku
      } : null
    });
    
    for (const i of data.items) {
      // Skip excluded items (returned items) only if feature flag is enabled
      // For split orders: compare against sales_order_item_id (links to original sales_order_item.item_id)
      // For regular orders: compare against item_id (which is the sales_order_item.item_id)
      let itemIdToCompare;
      if (hasSalesOrderItemId && i.sales_order_item_id !== undefined && i.sales_order_item_id !== null) {
        // Split order item: use sales_order_item_id to match with excludedOrderItemIds
        itemIdToCompare = Number(i.sales_order_item_id);
      } else {
        // Regular order item: use item_id directly
        itemIdToCompare = Number(i.item_id);
      }
      
      const isExcluded = shukranOnShipmentLevel && excludedOrderItemIdsAsNumbers.length > 0 && excludedOrderItemIdsAsNumbers.includes(itemIdToCompare);
      
      if (isExcluded) {
        excludedItemsCount++;
        console.log(`${logPrefix} createTransactionDetails: Excluding returned item (feature flag enabled)`, {
          item_id: i.item_id,
          sales_order_item_id: i.sales_order_item_id,
          item_id_to_compare: itemIdToCompare,
          is_split_order_item: hasSalesOrderItemId && i.sales_order_item_id !== undefined,
          sku: i.sku,
          excludedOrderItemIds: excludedOrderItemIds,
          excludedOrderItemIdsAsNumbers: excludedOrderItemIdsAsNumbers,
          excludedItemsCount
        });
        continue;
      }
      
      if (
        i.product_type === 'simple' &&
        i.qty_ordered &&
        parseInt(i.qty_ordered) - parseInt(i.qty_canceled || 0) > 0
      ) {
        const itemQuantity =
          parseInt(i.qty_ordered) - parseInt(i.qty_canceled || 0);
        const qtyOrdered = parseInt(i.qty_ordered) || 1;
        const discountProportion = qtyOrdered > 0 ? itemQuantity / qtyOrdered : 1;
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
          TransactionNumber: data.transactionNumber,
          OriginalStoreCode: '',
          OriginalTransactionDateTime: '',
          OriginalTransactionNumber: '',
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
        // For split orders with partial cancellations, calculate price based on actual quantity (qty_ordered - qty_canceled)
        // itemQuantity is already calculated as qty_ordered - qty_canceled
        const itemSubTotal =
          (parseFloat(i.original_price) / taxFactor) * itemQuantity;
        const itemDiscount1 =
          ((parseFloat(i.original_price) - parseFloat(i.price_incl_tax)) /
            taxFactor) *
          itemQuantity;
        
        // For split orders, use split_entity_id (split_sales_order.entity_id) for discount lookup
        // For regular orders, use order_id (sales_order.entity_id)
        const isSplitOrderItem = (i.sales_order_item_id !== undefined && i.sales_order_item_id !== null) || 
                                  (data.split_entity_id !== undefined && data.split_entity_id !== null);
        const orderIdForDiscount = isSplitOrderItem && data.split_entity_id ? data.split_entity_id : i.order_id;
        const discountAmount = await this.getDiscountAmount(
          orderIdForDiscount,
          i.parent_item_id,
          isSplitOrderItem
        );
        global.logInfo(
          `DiscountAmount , ${data.TransactionNumber} ,${discountAmount}`
        );
        // Calculate itemDiscount2 proportionally based on itemQuantity / qty_ordered (discountProportion defined above)
        const itemDiscount2 =
          ((parseFloat(i.discount_amount) - parseFloat(discountAmount)) /
            taxFactor) * discountProportion;
        const totalDiscount = itemDiscount1 + itemDiscount2;

        // DollarValueNet is calculated directly for the actual quantity (no division needed)
        itemData.DollarValueNet = parseFloat(
          (itemSubTotal - totalDiscount).toFixed(2)
        );
        
        // Log price calculation details for split orders with partial cancellations
        if (isSplitOrderItem && parseInt(i.qty_canceled || 0) > 0) {
          console.log(`${logPrefix} createTransactionDetails: Split order partial cancellation price calculation`, JSON.stringify({
            sku: i.sku,
            item_id: i.item_id,
            sales_order_item_id: i.sales_order_item_id,
            qty_ordered: i.qty_ordered,
            qty_canceled: i.qty_canceled,
            itemQuantity: itemQuantity,
            original_price: i.original_price,
            price_incl_tax: i.price_incl_tax,
            discount_amount: i.discount_amount,
            discountAmount: discountAmount,
            taxFactor: taxFactor,
            itemSubTotal: itemSubTotal,
            itemDiscount1: itemDiscount1,
            itemDiscount2: itemDiscount2,
            discountProportion: discountProportion,
            totalDiscount: totalDiscount,
            DollarValueNet: itemData.DollarValueNet,
            Quantity: itemQuantity
          }, null, 2));
        }
        finalOrderPrice = finalOrderPrice + itemData.DollarValueNet;
        itemData.TaxAmount = 0;
        itemData.DiscountAmount = 0;
        transactionData.push(itemData);
        lineNumber++;
      }
    }
    if (data.isCod) {
      const randomNumber = await this.generateRandomNumber();
      global.logInfo(
        `randomNumber , ${data.TransactionNumber} ,${randomNumber}`
      );
      transactionData.push({
        DollarValueGross: 0,
        DollarValueNet: parseFloat(data.orderNetPrice),
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
        TransactionNumber: data.transactionNumber,
        OriginalStoreCode: null,
        OriginalTransactionDateTime: null,
        OriginalTransactionNumber: null,
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

    console.log(`${logPrefix} createTransactionDetails: Transaction details created`, {
      transactionDataCount: transactionData.length,
      finalOrderPrice: parseFloat(finalOrderPrice.toFixed(2)),
      totalQty,
      excludedItemsCount: excludedItemsCount,
      shukranOnShipmentLevel,
      excludedOrderItemIds: excludedOrderItemIds,
      excludedOrderItemIdsAsNumbers: excludedOrderItemIdsAsNumbers,
      includedItemIds: transactionData.map(td => {
        // Extract item_id from transaction data if available
        const item = data.items?.find(i => Number(i.item_id) === Number(td.ItemNumber) || i.sku === td.ItemNumber);
        return item ? { item_id: item.item_id, sku: item.sku } : null;
      }).filter(Boolean)
    });

    return {
      transactionData: transactionData,
      finalOrderPrice: parseFloat(finalOrderPrice.toFixed(2)),
      totalQty
    };
  } catch (err) {
    console.error(`${logPrefix} createTransactionDetails: Error creating transaction details`, {
      error: err.message,
      shukranOnShipmentLevel: data.shukranOnShipmentLevel
    });
    throw new Error(err.message);
  }
};

exports.getDiscountAmount = async (orderId, itemId, isSplitOrder = false) => {
  let discountAmount = 0;
  if (itemId) {
    if (isSplitOrder) {
      // For split orders, use split_sub_sales_order_item
      const { SplitSubSalesOrderItem } = require('../models/seqModels/index');
      const splitSubSalesOrderItem = await SplitSubSalesOrderItem.findOne({
        where: {
          parent_order_id: orderId,
          main_item_id: itemId,
          is_gift_voucher: true
        },
        attributes: ['sub_item_id', 'discount']
      });
      if (splitSubSalesOrderItem) {
        discountAmount = splitSubSalesOrderItem.discount;
      }
    } else {
      // For regular orders, use sub_sales_order_item
      const salesOrderItem = await orderObj.getSubSalesOrderItem(orderId, itemId);
      if (salesOrderItem) {
        discountAmount = salesOrderItem.discount;
      }
    }
  }
  return discountAmount;
};

exports.generateRandomNumber = async () => {
  return crypto.randomInt(0, 100000).toString().padStart(5, '0');
};
