const {
  getStores,
  getStoresWebsite,
  createNewWebsite,
  createNewStore,
  // getNewStore,
  getStoreDetails,
  updateStoreDetails
} = require('../helpers/store');
const catchAsync = require('../utils/catchAsync');
const httpStatus = require('http-status');
const consul = require('consul');

const storeList = catchAsync(async (req, res) => {
  try {
    const response = await getStores();
    const payload = {
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    console.log(e);
    res.status(500).json({ error: e.message });
  }
});

const websiteList = catchAsync(async (req, res) => {
  try {
    const response = await getStoresWebsite();
    const payload = {
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    console.log(e);
    res.status(500).json({ error: e.message });
  }
});

const createWebsite = catchAsync(async (req, res) => {
  try {
    // console.log(req.body);
    const website = req.body;
    const response = await createNewWebsite(website);
    const payload = {
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    console.log(e);
    res.status(500).json({ error: e.message });
  }
});

const createStore = catchAsync(async (req, res) => {
  try {
    const store = req.body;
    const response = await createNewStore(store);
    // let newStore;
    // const consulObj = await consul({
    //   host: process.env.CONSUL_HOST,
    //   port: process.env.CONSUL_PORT
    // });
    // await getAppConfig(process.env.CONSUL_APP_CONFIG_ENV, function (result) {
    //   const consul_value = result.response;
    //   newStore = getNewStore(response, store);
    //   consul_value.environments[0].stores.push(newStore);
    //   let in_response = {};
    //   consulObj.kv.set(
    //     process.env.CONSUL_APP_CONFIG_ENV,
    //     JSON.stringify(consul_value),
    //     function (err, ress) {
    //       if (err) {
    //         in_response = {
    //           status: false,
    //           statusCode: '400',
    //           statusMsg: err
    //         };
    //       } else {
    //         in_response = {
    //           status: true,
    //           statusCode: '200',
    //           statusMsg: 'Success',
    //           response: ress
    //         };
    //       }
    //       console.log(in_response);
    //     }
    //   );
    // });

    const payload = {
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    console.log(e);
    res.status(500).json({ error: e.message });
  }
});

// const getAppConfig = async (key, callback) => {
//   const consulObj = await consul({
//     host: process.env.CONSUL_HOST,
//     port: process.env.CONSUL_PORT
//   });
//   let response = {};
//   consulObj.kv.get(key, function (err, result) {
//     if (err) {
//       response = {
//         status: false,
//         statusCode: '400',
//         statusMsg: err
//       };
//     } else {
//       response = {
//         status: true,
//         statusCode: '200',
//         statusMsg: 'Success',
//         response: JSON.parse(result.Value)
//       };
//     }
//     callback(response);
//   });
// };

const getWarehouseLocation = catchAsync(async (req, res) => {
  const consulObj = await consul({
    host: process.env.CONSUL_HOST,
    port: process.env.CONSUL_PORT
  });
  try {
    let payload = {};
    consulObj.kv.get(
      'java/order-service/' + process.env.CONSUL_CREDENTIALS_ENV,
      function (err, result) {
        if (err) {
          payload = {
            status: false,
            statusCode: '400',
            statusMsg: err
          };
        } else {
          const response = JSON.parse(result?.Value).inventory_mapping;
          payload = {
            status: true,
            statusCode: '200',
            statusMsg: 'Success',
            response
          };
        }
        res.status(httpStatus.OK).json(payload);
      }
    );
  } catch (e) {
    console.log(e);
    res.status(500).json({ error: e.message });
  }
});

const getStore = catchAsync(async (req, res) => {
  const {
    body: { store_id }
  } = req;
  console.log('store_id', store_id);
  if (!store_id)
    res.status(500).json({
      status: true,
      statusCode: '500',
      statusMsg: 'store id required'
    });

  try {
    const response = await getStoreDetails(store_id);
    const payload = {
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    console.log(e);
    res.status(500).json({ error: e.message });
  }

  // res.status(httpStatus.OK).json({});
});

const updateStoreDetail = catchAsync(async (req, res) => {
  try {
    const reqObj = req?.body?.options || {};
    const response = await updateStoreDetails(reqObj);
    // let newStore;
    // const consulObj = await consul({
    //   host: process.env.CONSUL_HOST,
    //   port: process.env.CONSUL_PORT
    // });
    // await getAppConfig(process.env.CONSUL_APP_CONFIG_ENV, function (result) {
    //   const consulValue = result.response;
    //   newStore = getNewStore(reqObj, '');
    //   consulValue.environments[0].stores.push(newStore);
    //   let inResponse = {};
    //   consulObj.kv.set(
    //     process.env.CONSUL_APP_CONFIG_ENV,
    //     JSON.stringify(consulValue),
    //     function (err, ress) {
    //       if (err) {
    //         inResponse = {
    //           status: false,
    //           statusCode: '400',
    //           statusMsg: err
    //         };
    //       } else {
    //         inResponse = {
    //           status: true,
    //           statusCode: '200',
    //           statusMsg: 'Success',
    //           response: ress
    //         };
    //       }
    //       console.log(inResponse);
    //     }
    //   );
    // });
    res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = {
  getStore,
  storeList,
  websiteList,
  createWebsite,
  createStore,
  getWarehouseLocation,
  updateStoreDetail
};
