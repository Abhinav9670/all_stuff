const axios = require('axios');
const {
  CUSTOMER_LIST_ENDPOINT,
  CUSTOMER_DETAIL_ENDPOINT,
  CUSTOMER_UPDATE_ENDPOINT,
  ADDRESS_LIST_ENDPOINT,
  ADDRESS_UPDATE_ENDPOINT,
  WALLET_LIST_ENDPOINT,
  WALLET_ADD_ENDPOINT,
  CUSTOMER_DEVICE_DELETE_ENDPOINT
} = require('../constants/javaEndpoints');
const { ADDRESS_MAPPER_URL, STORE_TIME_ZONE_MAP } = require('../constants/index');
const catchAsync = require('../utils/catchAsync');
const { getStoreConfigs } = require('../utils/config');
const { addAdminLog } = require('../helpers/logging');
const { getDeletedCustomers } = require('../helpers/customer');
const {
  getCustomerShukranProfile
} = require('../shukran/action');
const { sequelize } = require('../models/seqModels/index');
const { QueryTypes } = require('sequelize');
const {
  AmastyStoreCredit,
  AmastyStoreCreditHistory
} = require('../models/seqModels/index');
const moment = require("moment-timezone");

const customerList = catchAsync(async (req, res) => {
  try {
    console.log(`Customer service OMS URL : ${CUSTOMER_LIST_ENDPOINT}`);
    const { body } = req;
    const response = await axios.post(CUSTOMER_LIST_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { status, data } = response;
    res.status(status).json(data);
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});
const addressList = catchAsync(async (req, res) => {
  const { params } = req.body;
  const { customerId } = params;
  const response = await axios.post(
    ADDRESS_LIST_ENDPOINT,
    { customerId },
    {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    }
  );
  const { status, data } = response;
  res.status(status).json(data);
  // res.status(200).json({
  //   status: true,
  //   statusCode: '200',
  //   statusMsg: 'SUCCESS',
  //   response: {
  //     address: null,
  //     message: null,
  //     addresses: [
  //       {
  //         customerId,
  //         addressId: 56,
  //         firstName: 'veev',
  //         lastName: 'eve',
  //         mobileNumber: '+966 559098780',
  //         city: 'Bader',
  //         fax: null,
  //         streetAddress: 'dcefe',
  //         telephone: null,
  //         country: 'SA',
  //         region: 'Al-Madinah Al-Monawarah',
  //         defaultAddress: false,
  //         postCode: null,
  //         regionId: 580,
  //         area: 'Al Ghazwah',
  //         landMark: 'efefewfe',
  //         buildingNumber: 'efefe'
  //       },
  //       {
  //         customerId: 2269424,
  //         addressId: 58,
  //         firstName: 'veev',
  //         lastName: 'eve',
  //         mobileNumber: '+966 590909098',
  //         city: 'Al Adari',
  //         fax: null,
  //         streetAddress: 'eveve',
  //         telephone: null,
  //         country: 'SA',
  //         region: 'Al-Jouf{Al Jawf}',
  //         defaultAddress: true,
  //         postCode: null,
  //         regionId: 579,
  //         area: 'Al Adari',
  //         landMark: 'evewvew',
  //         buildingNumber: 'eveavve'
  //       }
  //     ]
  //   },
  //   error: null
  // });
});
const customerDetail = catchAsync(async (req, res) => {
  try {
    const { params } = req.body;
    const { customerId } = params;
    global.logInfo(
      `getCustomerShukranProfile customer id, ${
        customerId ? customerId : ''
      }`
    );
    const response = await axios.post(
      CUSTOMER_DETAIL_ENDPOINT,
      { customerId },
      {
        headers: {
          Authorization: req.headers?.authorization || ''
        }
      }
    );
    const { status, data } = response;

    global.logInfo(
      `getCustomerShukranProfile customer data, ${
        data ? JSON.stringify(data) : ''
      }`
    );

    if(data?.response?.customer?.shukranLinkFlag){
      global.logInfo(
      `getCustomerShukranProfile shukran Link flag, ${
        data.response.customer.shukranLinkFlag
      }`
    );
      data.response.customer.availablePoints= await getCustomerShukranProfile(data?.response?.customer?.mobileNumber, data?.response?.customer?.storeId)
    }
    return res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const deleteCustomer = catchAsync(async (req, res) => {
  try {
    const { params } = req.body;
    const { customerId, deviceIds } = params;
    const response = await axios.post(
      CUSTOMER_DEVICE_DELETE_ENDPOINT,
      { customerId ,deviceIds},
      {
        headers: {
          Authorization: req.headers?.authorization || ''
        }
      }
    );
    const {statusMsg } = response.data;
    return res.status(response?.status).json({statusMsg});
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const addressMap = catchAsync(async (req, res) => {
  try {
    const { params } = req;
    const { countryLocale } = params;
    const response = await axios.get(
      `${ADDRESS_MAPPER_URL}/address_${countryLocale}.json`
    );
    const { data } = response ?? {};
    res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});
const addressUpdate = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const { beforeData, afterData } = body;
    // const { countryLocale } = params;
    const response = await axios.put(ADDRESS_UPDATE_ENDPOINT, afterData, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });

    const { data } = response ?? {};
    afterData.regionId = afterData.regionId.toString();
    const logData = {
      before: beforeData,
      after: afterData
    };
    addAdminLog({
      type: 'customer',
      data: logData,
      email,
      desc: 'Address Update'
    });
    res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});
const customerUpdate = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const { beforeData, afterData } = body;
    const response = await axios.put(
      CUSTOMER_UPDATE_ENDPOINT,
      {
        ...afterData,
        omsRequest: true
      },
      {
        headers: {
          Authorization: req.headers?.authorization || ''
        }
      }
    );
    const { data } = response ?? {};
    const logData = {
      before: beforeData,
      after: afterData
    };
    addAdminLog({
      type: 'customer',
      data: logData,
      email,
      desc: 'Customer Detail Update'
    });
    res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const convertTimeZone = (datetime, storeId) => {
  if (!datetime) return null;
  const timeZone = STORE_TIME_ZONE_MAP[storeId] || "UTC";
  return moment(datetime).tz(timeZone).format("YYYY-MM-DD HH:mm:ss");
}

const customerWalletList = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    const { customerId, storeId} = body;
    const conversionRates = getStoreConfigs({
      key: ['currencyConversionRate', 'websiteId', 'storeCurrency']
    });
    
    const baseConfig = global.baseConfig;
    const omsWalletListEnable = baseConfig?.apiOptimization?.omsWalletListEnable ?? false;
    if(omsWalletListEnable){
      const appConfig = global.config;
      const storesData = appConfig?.environments?.[0]?.stores;
      const store = storesData.find(e => Number(e.storeId) === storeId) || null;
      let statusMessage = "no styli credit/debit found";
      if (!store?.storeCode || !store?.storeCurrency) {
          res.status(200).json({ ...{
            "status": false,
            "statusCode": "202",
            "statusMsg": "Store not found!",
            "response": null,
            "returnableAmount": null,
            "totalAmount": null,
            "error": null,
          }, customerId, conversionRates });
      }

      let storeCreditList = [];
      let returnableAmountBalance = 0;
      let totalAmountBalance = 0;
      const storeCredit = await AmastyStoreCredit.findOne({
        where: { customer_id: customerId }
      });
      if (storeCredit?.returnable_amount != null) {
        returnableAmountBalance = storeCredit.returnable_amount;
      }

      if (storeCredit?.store_credit != null) {
        totalAmountBalance = storeCredit.store_credit;
      }

      const convertedTotalAmount = (totalAmountBalance / store.currencyConversionRate).toFixed(4);
      const convertedReturnableAmount = (returnableAmountBalance / store.currencyConversionRate).toFixed(4);
      const creditHistories = await AmastyStoreCreditHistory.findAll({
        where: { customer_id: customerId },
        order: [['created_at', 'DESC']]
      });
      const actionMapping = {
        1: "Changed By Admin",
        2: "Bank transfer",
        6: "Referral",
        7: "Admin Refund",
        8: "Finance Bulk Update",
        9: "braze_promo"
      };
      if (creditHistories?.length) {
        creditHistories.forEach(styliCredit => {
          const storeCredit = {};
          
          if (styliCredit.action_data?.includes('"')) {
            storeCredit.orderId = (styliCredit.action_data.match(/\["(.*?)"\]/)?.[1] || null);
          }
          if (styliCredit.created_at) {
              storeCredit.date = convertTimeZone(styliCredit.created_at, storeId);
          }
          if (styliCredit.difference) {
            let convertedStoreCreditDiff = parseFloat(styliCredit.difference / store.currencyConversionRate).toFixed(1);
            convertedStoreCreditDiff = (convertedStoreCreditDiff.startsWith("0.") ? convertedStoreCreditDiff.slice(1) : convertedStoreCreditDiff);
            storeCredit.amount = convertedStoreCreditDiff;
          }
          if (styliCredit.store_credit_balance) {
            let convertedStoreCreditBalance = parseFloat(styliCredit.store_credit_balance / store.currencyConversionRate).toFixed(1);
            convertedStoreCreditBalance = (convertedStoreCreditBalance.startsWith("0.") ? convertedStoreCreditBalance.slice(1) : convertedStoreCreditBalance);
            storeCredit.balance = convertedStoreCreditBalance;
          }
          storeCredit.isCredit = (styliCredit.is_deduct !== null && styliCredit.is_deduct === 0);
          
          if (styliCredit.action !== null && actionMapping[styliCredit.action]) {
              storeCredit.actionData = actionMapping[styliCredit.action];
          }

          if (!storeCredit.orderId || storeCredit.orderId === "null") {
              storeCredit.orderId = storeCredit.actionData;
          }
          storeCredit.comment = styliCredit.message;

          // Push the result to storeCreditList
          storeCreditList.push(storeCredit);
        });
        statusMessage = "fetched successfully";
      }

      res.status(200).json({ ...{
        "status": true,
        "statusCode": "200",
        "statusMsg": statusMessage,
        "response": storeCreditList,
        "returnableAmount": convertedReturnableAmount,
        "totalAmount": convertedTotalAmount,
        "error": null,
      }, customerId, conversionRates });
    } else {
      const response = await axios.post(WALLET_LIST_ENDPOINT, body, {
        headers: {
          Authorization: req.headers?.authorization || ''
        }
      });
      const { data } = response ?? {};
      res.status(200).json({ ...data, customerId, conversionRates });
    }    
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});
const customerWalletUpdate = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const response = await axios.post(WALLET_ADD_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { data } = response ?? {};
    addAdminLog({
      type: 'customer',
      data: body,
      email,
      desc: 'Wallet Update'
    });
    res.status(200).json({ ...data });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});
const deletedCustomersList = catchAsync(async (req, res) => {
  const { offset, pageSize, query } = req.body;
  if (!pageSize || Number.isNaN(+pageSize)) {
    return res
      .status(400)
      .json({
        code: 400,
        message:
          'Invalid page size parameter. Page size must be a positive integer.'
      });
  }
  try {
    const object = {
      offset,
      limit: +pageSize,
      query
    };
    const response = await getDeletedCustomers(object);
    res.status(200).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Deleted Customers fetched!',
      response: { offset, pageSize: +pageSize, ...response }
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});
module.exports = {
  customerList,
  customerDetail,
  addressList,
  addressMap,
  addressUpdate,
  customerUpdate,
  customerWalletList,
  customerWalletUpdate,
  deletedCustomersList,
  deleteCustomer
};
