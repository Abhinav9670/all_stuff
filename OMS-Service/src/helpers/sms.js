/* eslint-disable max-lines-per-function */
const moment = require('moment');
const { STORE_LANG_MAP, SPLIT_ORDER_SMS_TYPE } = require('../constants')
const { sendKaleyraSMS } = require('../services/misc.service');
const { replaceAllString, getNumericValue, getPercentage, maskPhoneNumber } = require('../utils');
const { earnCheckIsRatingOnOrder } = require('./eas/earnIntegration');
const { getTemplateMap } = require('./smsTemplates1');
const { sendSgEmail } = require('../services/email.service');
const { prepareHtml,getOrderData } = require('./email');
const { getStoreLink, getWebsiteLink, getProductUrl, getEstDelivery } = require('./store');
const { getStoreConfigs } = require('../utils/config');
const logger = require('../config/logger');
const { REFUND_TEMPLATE_MAP } = require('../constants/order');
const { isSplitOrderPattern, extractBaseIncrementId } = require('../utils/splitOrderUtils');
const emailOBJ = require('./email');
const { SMS_TYPE_IS_RESTRICTED } = require('../constants/errorTypes');



const getTemplateVars = ({
  orderData,
  returnData,
  isReturn,
  smsType,
  ratingCoinEnabled,
  timestamp
}) => {
  global.logInfo('getTemplateVars');
  const {
    shipmentData = [],
    //  increment_id,
    estimated_delivery_time,
    configItems,
    order_currency_code,
    entity_id
  } = orderData;
  const {
    rmaItems: returnItems = [],
    rmaTracking = {},
    address: returnShippingAddress,
    rmaData,
    creditMemo = {}
  } = returnData || {};
  const cancelCreditMemo = {
    grand_total: orderData?.grand_total,
    order_currency_code: orderData.order_currency_code,
    amstorecredit_amount: orderData.amstorecredit_amount
  };
  const {
    amstorecredit_amount = '0.0000',
    order_currency_code: creditMemoCurrency,
    grand_total,
    sms_money = '0.00'
  } = isReturn ? creditMemo : cancelCreditMemo;
  const onlineRefundAmount =
     Number(sms_money)
      ? getNumericValue(Number(sms_money))
      : getNumericValue(
        Number(grand_total) + Number(amstorecredit_amount || 0)
      );   
  const creditRefunded = getNumericValue(amstorecredit_amount);
  const { store_id: storeId, order_inc_id, rma_inc_id: returnId } = isReturn
    ? rmaData
    : orderData;
  const { smsTemplate, carrierCodes, smsTemplateId } = getConfigs({
    store_id: storeId,
    smsType,
    ratingCoinEnabled
  });
  const canceledItemCount = getCancelledItemCount(configItems);
  const incrementId = isReturn ? order_inc_id : orderData.increment_id;

  rmaTracking.title = carrierCodes.find(
    cc => cc.code === String(rmaTracking.tracking_code)
  )?.label;
  rmaTracking.track_number = rmaTracking?.tracking_number;

  const estimatedDelivery = getEstDelivery(estimated_delivery_time);
const pickupFailedDate=getEstDelivery(timestamp)
  const { tracking_number: returnAwb } = rmaTracking;

  let returnItemText = returnItems.length
    ? `${returnItems[0].name} + ${returnItems.length - 1}`
    : '';

  if (returnItems.length === 1) returnItemText = returnItems[0].name;

  const { firstname, lastname, telephone: shippingPhone } = isReturn
    ? returnShippingAddress
    : orderData.shippingAddress;
  const { title: courierName, track_number: awbNumber } =
  (isReturn ? rmaTracking : shipmentData[0]) || {};
  return {
    smsTemplate,
    firstname,
    lastname,
    incrementId,
    courierName,
    awbNumber,
    estimatedDelivery,
    returnId,
    returnAwb,
    returnItemText,
    creditMemoCurrency,
    creditRefunded,
    onlineRefundAmount,
    canceledItemCount,
    order_currency_code,
    shippingPhone,
    entityId: entity_id,
    itemReturned: returnItems.length,
    smsTemplateId: smsTemplateId,
    returnUrl: returnData?.returnUrl,
    returnData: returnData,
    storeId,
    pickupFailedDate
  };
};

const getConfigs = ({ store_id, smsType, ratingCoinEnabled = false }) => {
  let smsTemplate =
    global?.baseConfig?.smsConfig?.templates?.[store_id]?.[smsType];
  const carrierCodes = global?.baseConfig?.configs?.carrierCodes;
  let smsTemplateId =
    global?.baseConfig?.smsConfig?.templateIds?.[store_id]?.[smsType];
  if (ratingCoinEnabled) {
    smsTemplate =
    global?.baseConfig?.smsConfig?.templates?.[store_id]?.[
      `${smsType}_rating_coins`
      ];
    smsTemplateId =
      global?.baseConfig?.smsConfig?.templateIds?.[store_id]?.[
        `${smsType}_rating_coins`
      ];
  }
  return { smsTemplate, carrierCodes, smsTemplateId };
};

const getCancelledItemCount = configItems => {
  return configItems?.reduce((count, item) => {
    count = count + Number(item.qty_canceled);
    return count;
  }, 0);
};

exports.sendSMS = async ({
  currency,
  smsType,
  orderData = {},
  registerCustomerName,
  returnData,
  isReturn,
  missingItems,
  codPartialCancelAmount,
  returnedItemsResponse,
  cpId,
  timestamp,
  refundTrigger= false,
  notificationId = 0,
  splitOrderData={},
  incrementIdData=''
}) => {
  try{
    const baseConfig = global.baseConfig;
    const omsOrderSMSEnable = baseConfig?.apiOptimization?.omsOrderSMSEnable ?? false;

  let response = { errorMsg: '', sent: false };

    const excludedMessageHeadings = global.baseConfig?.smsConfig?.excludedMessageHeadings || [];
    const SMSExcludedCourierPartners = global.baseConfig?.smsConfig?.SMSExcludedCourierPartners || [];
    if (excludedMessageHeadings.includes(smsType) ||
      SMSExcludedCourierPartners.includes(Number(cpId))) {
      console.log('SMS :::: Excluded SMS is Encountered ');
      return { status: true, msg: `${smsType} ${SMS_TYPE_IS_RESTRICTED}` };
    }
  const ratingCoinEnabled = orderData?.entity_id
    ? await earnCheckIsRatingOnOrder({
        orderId: orderData.entity_id,
        smsType: smsType
      })
    : false;
    const isSplitOrder = isSplitOrderPattern(incrementIdData) || false;
    const splitOrderChangeStatus = SPLIT_ORDER_SMS_TYPE.includes(smsType) || false;
    if (isSplitOrder && splitOrderChangeStatus) {
      smsType = `${smsType}_split_order`;
    }
  const templateVars = getTemplateVars({
    orderData,
    returnData,
    isReturn,
    missingItems,
    smsType,
    ratingCoinEnabled,
    timestamp
  });
  const { smsTemplate, shippingPhone, smsTemplateId, storeId, incrementId } = templateVars;

  const { creditMemo: cancelCreditMemo = {} } = orderData;
  const { creditMemo = {} } = returnData || {};
  const { entity_id: creditmemoEntityId } = isReturn
    ? creditMemo
    : cancelCreditMemo;

  const estimatedDeliveryDiff = orderData?.estimated_delivery_time
    ? moment().diff(moment(orderData?.estimated_delivery_time), 'minutes')
    : 0;

  if (estimatedDeliveryDiff >= 0 && smsType === 'shipped') {
    return { sent: true, status: true };
  }

  const currencyData = getStoreConfigs({
    key: 'storeCurrency',
    storeId
  })?.[0]?.storeCurrency;

  try {
    let replaceMap = {};
    if (smsType) {
      if (!smsTemplate) {
        response.errorMsg = 'Template not found';
        return response;
      }
      if (!shippingPhone) {
        response.errorMsg = 'Phone number not found';
        return response;
      }

      replaceMap = getTemplateMap({
        codPartialCancelAmount,
        smsType,
        registerCustomerName,
        missingItems,
        currency,
        returnedItemsResponse,
        splitOrderData,
        incrementIdData,
        ...templateVars,
        cpId
      });

      const sms = replaceAllString(smsTemplate, replaceMap);
      
      const smsNotSend = global?.baseConfig?.smsNotSend;

      const language = STORE_LANG_MAP[Number(storeId)];

      const skipSms=global?.baseConfig?.skipSms
      console.log(
        `### SMS : ${sms}, Phone: ${shippingPhone}, template: ${smsType}, SMS Temp: ${JSON.stringify(
          smsNotSend?.[smsType] || {}
        )}`
      );
      
      if (smsNotSend?.[smsType]) {
        if (smsType=== "delivered"){
          console.log(`DELIVERED CASE STARTED FOR EMAIL TEMPLATE ######## ${smsType}&&&&& ${JSON.stringify(orderData)}`);
          omsOrderSMSEnable ? sendMail({ smsType, orderData, currency, language, storeId}) : await sendMail({ smsType, orderData, currency, language, storeId});
          console.log(`DELIVERED CASE RETURNED AFTER SEND MAIL PROCESS ######## ${smsType}`);
          response = {
            sent: true,
            kaleyra: {},
            status: true,
            sms,
            creditmemoEntityId,
            orderData
          };
        }
        else {
          let toEmail = '';
        if(orderData?.customer_email){
          toEmail = orderData?.customer_email || '';
        }else{
          toEmail = returnData?.address?.email || '';
        }
        console.log(
          `SMS send mail instead template: ${smsType}, msg: ${sms}, Email: ${toEmail}, status: ${smsNotSend[smsType]?.status}`
        );
        if (toEmail != '') {
          if (smsNotSend[smsType]?.status) {
            const { fromEmail, fromName } =
            global?.baseConfig?.emailConfig || {};
            let subject = language === 'ar' ? smsNotSend[smsType].subject_ar : smsNotSend[smsType].subject;
            if (language == 'ar' && isReturn) {
              subject = `تحديث لطلب الاسترجاع - رقم الطلب \u202A#{{returnIncrementId}}`;
            }
            subject = subject.replace(
              '{{incrementId}}',
              orderData?.increment_id
            );

            subject = subject.replace(
              '{{returnIncrementId}}',
              returnData?.rmaData?.rma_inc_id
            );
            let formattedProducts = [];
            if (isReturn && !returnData?.rmaItems?.[0]?.OrderItem?.item_img_url) {
            const omsOrderData = await getOrderData(returnData?.rmaData?.order_id);
            formattedProducts = omsOrderData?.products;
            }
            /* Prepare email */
            let htmlD;
            const lang = STORE_LANG_MAP[returnData?.rmaData?.store_id];
            switch (smsType) {
              case 'return_create':

                htmlD = prepareHtml({
                  template: `returnRequest_${lang}`,
                  data: {
                    name: returnData?.address?.firstname,
                    orderId: returnData?.rmaData?.order_inc_id,
                    returnId: returnData?.rmaData?.rma_inc_id,
                    customerPhone: returnData?.address?.telephone,
                    customerStreet: returnData?.address?.street,
                    customerArea: returnData?.address?.area,
                    customerCity: returnData?.address?.city,
                    customerRegion: returnData?.address?.region,
                    currency,
                    data: returnData?.rmaItems?.map(item => {
                      const originalPrice = item.OrderItem?.original_price || 0;
                      const priceInclTax = item.OrderItem?.price_incl_tax || 0;
                      const productDetails = formattedProducts.find(product => product.sku === item?.OrderItem?.sku);
                    
                      // Calculate the price difference
                      discountPercentage = getPercentage(getNumericValue(originalPrice),getNumericValue(priceInclTax));
                      const url = getProductUrl(storeId, [{ name: item.OrderItem?.name, sku: item.OrderItem?.sku }])[0];
                      const OrderItems={
                        ...item.OrderItem,
                        item_img_url: productDetails?.image || item.OrderItem?.item_img_url,
                        item_brand_name: productDetails?.brandName || item.OrderItem?.item_brand_name,
                        item_size: productDetails?.size || item.OrderItem?.item_size,
                        currencyVal:currencyData
                      }
                      // Return the modified item with priceDiff added
                      return {
                        ...item,
                        OrderItem: OrderItems,
                        discountPercentage,
                        url
                      };
                    }),
                    storeLink: getStoreLink(storeId),
                    websiteLink:getWebsiteLink(storeId),
                    incrementId
                  },
                });
                break;


                case 'return_awb_create':
                  const processedReturnData = returnData?.rmaItems?.map(item => {
                    const originalPrice = item.OrderItem?.original_price || 0;
                    const priceInclTax = item.OrderItem?.price_incl_tax || 0;
                    const productDetails = formattedProducts.find(product => product.sku === item?.OrderItem?.sku);
                
                    // Calculate the discount percentage
                    const discountPercentage = getPercentage(
                      getNumericValue(originalPrice),
                      getNumericValue(priceInclTax)
                    );
                    const url = getProductUrl(storeId, [{ name: item.OrderItem?.name, sku: item.OrderItem?.sku }])[0];
                    const OrderItems={
                      ...item.OrderItem,
                      item_img_url: productDetails?.image || item.OrderItem?.item_img_url,
                      item_brand_name: productDetails?.brandName || item.OrderItem?.item_brand_name,
                      item_size: productDetails?.size || item.OrderItem?.item_size,
                      currencyVal:currencyData
                    }
                    // Return the modified item with discountPercentage added
                    return {
                      ...item,
                      OrderItem: OrderItems,
                      discountPercentage,
                      url
                    };
                  });
                  htmlD = prepareHtml({
                    template: `${smsType}_${language}`,
                    data: {...templateVars,websiteLink:getWebsiteLink(storeId),processedReturnData, incrementId},
                  });
                  break;

              case 'shipped':
                htmlD = null; 
                break;

              default:
                htmlD = prepareHtml({
                  template: `default_${language}`,
                  data: {
                    sms,
                    storeLink: getStoreLink(storeId),
                    websiteLink:getWebsiteLink(storeId)
                  },
                });
                break;
            }
              if(htmlD){
              if(smsType === "return_create"){
                logger.info("SKIPPING EMAIL FOR RETURN ORDER PLACED FOR ALL STORES");
              }
              else {
                sendSgEmail({
                to: toEmail,
                from: { email: fromEmail, name: fromName },
                subject: subject,
                html: htmlD 
              });
              }
             }
          }
        }
        response = {
          sent: true,
          kaleyra: {},
          status: true,
          sms,
          creditmemoEntityId,
          orderData
        };
        }
      } else {
        let kaleyraResponse='';
        if(!skipSms.includes(smsType)){
          logger.info(`#SMS Sending ${smsType}`);
          const rmaValues = returnData?.rmaData;
          if (rmaValues?.method && REFUND_TEMPLATE_MAP.hasOwnProperty(rmaValues?.method) && !refundTrigger && notificationId === 2) {
            console.log(`SKIPPING SMS FOR REFUND ON PAYMENT METHODS FOR RETURN ID.....${returnData?.rmaData?.rma_inc_id} ${smsTemplate}`);
          }
          else {
            console.log(`SMS TEMPLATE VALUE FOR ORDER SUCCESS WATCHER ######### ${sms} ###### SMS TYPE ${smsType}`)
            kaleyraResponse = await sendKaleyraSMS({
              msg: sms,
              phone: shippingPhone,
              smsTemplateId: smsTemplateId
            });
          }
        }
        if (smsType === "order_prepaid_cancel" || smsType === "order_prepaid_cancel_split_order"|| smsType === "order_cod_cancel" || smsType === "order_cod_cancel_split_order") {
          omsOrderSMSEnable ? sendMail({ smsType, orderData , currency, language, storeId,sms}) : await sendMail({ smsType, orderData , currency, language, storeId,sms});
        }        
          response = {
            sent: true,
            kaleyra: kaleyraResponse,
            status: true,
            sms,
            creditmemoEntityId,
            orderData,
            incrementId,
            ...templateVars
          };
        }
      }
  } catch (e) {
    response.status = false;
    response.errorMsg = e.message? `Send Sms Error ${e.message}`: '';
    global.logError(e);
  }

  return response;
}catch(err){
  console.error('Error in sendMail2:', err.message? err.message:'', err);
  response.status = false;
    response.errorMsg = `Error in sendMail2: ${err.message}`;
    return response;
}
};

const sendMail = async ({ smsType, orderData, currency,language, storeId,sms}) => {

  let discountAmount=0;
  let taxAmount=0;
  let subtotalInlTax=0;
  let spendCoin;
  let formattedProducts = []; 
  console.log(`DELIVERY FLOW FOR SEND EMAIL STARTS FOR ${orderData?.entity_id}`);  
  if (!orderData?.configItems[0]?.item_img_url) {
  console.log(`ORDER DELIVERY FLOW REACHED IF BLOCK FOR SEND MAIL ${orderData?.entity_id}`);  
  const omsOrderData = await getOrderData({orderId:orderData?.entity_id});
  formattedProducts = omsOrderData?.products;
  }
  console.log(`SKIPPED IF BLOCK DELIVERY FLOW OR Reached outside of IF block ${orderData?.entity_id} and Formatted products ${JSON.stringify(formattedProducts)}`)  
  const updatedConfigItems = orderData?.configItems.map(item => {
    const discount = Number(item.discount_amount) || 0; 
    discountAmount += discount; 
    const taxval = Number(item.tax_amount) || 0;
    taxAmount += taxval
    const subTotalVal  = Number(item.price_incl_tax);
    subtotalInlTax += subTotalVal;
    let discountPercentage = getPercentage(getNumericValue(item.original_price),getNumericValue(item.price_incl_tax))
    const url = getProductUrl(storeId, [{ name: item.name, sku: item.sku }])[0];
    const productDetails = formattedProducts.find(product => product.sku === item.sku);
    
    return {
      ...item,
      currency: orderData?.order_currency_code,
      discountPercentage,
      url,
      item_img_url: productDetails?.image || item?.item_img_url,
      item_brand_name: productDetails?.brandName || item?.item_brand_name,
      item_size: productDetails?.size || item?.item_size
    };
  });

  const emailConfig = global?.baseConfig?.emailConfig;
  const { paymentMethodsLabel = {} } = emailConfig;

  let paymentMethod = paymentMethodsLabel[orderData?.paymentInformation[0].method] ? paymentMethodsLabel[orderData?.paymentInformation[0].method] : orderData?.paymentInformation[0].method
  
  let mainIncrementId = extractBaseIncrementId(orderData?.increment_id)|| '';
  let isSplitOrder = isSplitOrderPattern(orderData?.increment_id) || false;
  const splitSupportedTypes = ['delivered', 'order_prepaid_cancel_split_order'];
  let templateType = isSplitOrder && splitSupportedTypes.includes(smsType)
    ? `${smsType}_split_${language}`
    : `${smsType}_${language}`;
  
  let deliveryDate = orderData?.estimated_delivery_time;

  if (deliveryDate) {
    const today = new Date();
    const estimatedDate = new Date(deliveryDate);

    today.setHours(0, 0, 0, 0);
    estimatedDate.setHours(0, 0, 0, 0);

    if (estimatedDate > today) {
      deliveryDate = new Date();
    }
  }

  console.log(`CANCELLED AND DELIVERY FLOW REACHED HERE ${orderData?.entity_id}`);  
  const htmlData = prepareHtml({
    template: templateType,
    data: {
      orderIncrementId: orderData?.increment_id,
      shippingAddress: orderData?.shippingAddress,
      products: updatedConfigItems || [],
      currency,
      estimatedDeliveryTime: getEstDelivery(deliveryDate),
      paymentMethod,
      storeLink: getStoreLink(storeId),
      order_currency: orderData?.order_currency_code,
      websiteLink:getWebsiteLink(storeId),
      mainIncrementId,
      totals : {
        subtotalInlTax: Number(subtotalInlTax).toFixed(2),
        shippingAmount:Number(orderData?.shipping_amount) === 0?null :Number(orderData?.shipping_amount)?.toFixed(2),
        codCharges: Number(orderData?.cash_on_delivery_fee) === 0 ? null: Number(orderData?.cash_on_delivery_fee)?.toFixed(2),
        importFeesAmount: Number(orderData?.import_fee) === 0 ?  null: Number(orderData?.import_fee)?.toFixed(2),
        discountAmount : discountAmount===0?null:discountAmount?.toFixed(2),
        spendCoin,
        totalShukranEarnedPoints: orderData?.subSales?.total_shukran_coins_earned ? parseInt(orderData?.subSales?.total_shukran_coins_earned): null,
        shukranPhoneNumber: orderData?.subSales?.shukran_phone_number ? await maskPhoneNumber(orderData.subSales.shukran_phone_number): '',
        totalShukranBurnedPoints: orderData?.subSales?.total_shukran_coins_burned ? parseInt(orderData?.subSales?.total_shukran_coins_burned) : null,
        shukranTierName: orderData?.subSales?.tier_name,
        taxAmount: taxAmount === 0 ? null: taxAmount?.toFixed(2),
        currency:orderData?.order_currency_code,
        totalTaxableAmount: Number(orderData?.grand_total).toFixed(2),
        grandTotal: Number(orderData?.grand_total).toFixed(2),
    }

    }
  });
  console.log(`PASSED HTML PREPARE STEP FOR DELIVERY FLOW ${htmlData}`);  

  let sub = '';
  if (language === 'ar') {
    sub = smsType === 'delivered' ? 'تم توصيل الطلب' : 'تحديث استرداد الأموال';
  } else {
    sub = smsType === 'delivered' ? 'Order Delivered' : 'Refund Update';
  }

  if(smsType === "order_cod_cancel" || smsType === "order_cod_cancel_split_order"){
    let htmlNew = prepareHtml({
      template: `default_${language}`,
      data: {
        sms,
        storeLink: getStoreLink(storeId),
        websiteLink:getWebsiteLink(storeId)
      },
    });
    sendSgEmail({
      to: orderData?.customer_email,
      subject: language === 'ar'
    ? `${sub} - الطلب #${orderData?.increment_id}`
    : `${sub} - Order #${orderData?.increment_id}`,
      html: htmlNew
    });
  }
  else {
    sendSgEmail({
      to: orderData?.customer_email,
      subject: language === 'ar'
      ? `${sub} - الطلب #${orderData?.increment_id}`
      : `${sub} - Order #${orderData?.increment_id}`,
      html: htmlData
    });
  }
}