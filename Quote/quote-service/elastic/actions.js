const axios = require('axios');
const { logError, getStoreConfig } = require('../helpers/utils');
const { checkIsGwp } = require('../helpers/product');
const cache = require('memory-cache');


exports.getProducts = async ({ parentSkus, storeId, xHeaderToken }) => {
  let productArr = [];
  let flashSale = {};
  let errorMessage;
  let newFlashSale = [];
  const env = process.env.STYLI_ENV;

  let getProductEndpoint = process.env.GET_PRODUCT_ENDPOINT;
  const allSkus = [...parentSkus];
  const baseConfig = cache.get("baseConfig") || {};
  const enablev6getoptimization = baseConfig?.enablev6getoptimization || false;

  const countryCode = getStoreConfig(storeId, 'websiteCode') || '';
  const enableApigeeProductDetail = baseConfig?.enableApigeeProductDetail?.[countryCode] || false;
  if(enableApigeeProductDetail){
    getProductEndpoint = process.env.GET_PRODUCT_ENDPOINT_APIGEE;
  }
  const reqBody = {
    filters: { sku: allSkus },
    env: env,
    storeId: storeId,
    pageSize: allSkus.length,
    ...(enablev6getoptimization === true && { 
      is_cart: true,
      cart_additional_fields: ['short_description', 'is_dangerous_product']
    })
  };

  try {
    // logInfo(`Products from elastic search getProductEndpoint ::${getProductEndpoint}`);
    // logInfo(`Products from elastic search reqBody ::${JSON.stringify(reqBody)}`);
    
    const response = await axios.post(getProductEndpoint, reqBody, {
      headers: {
        'Content-Type': 'application/json',
        'x-header-token': xHeaderToken
      }
    });

    // logInfo(`Products from elastic search response status ::${response.status}`);

    const { hits } = response?.data || {};
    // const missingSkus = allSkus.filter(
    //   sku => !hits?.some(hit => hit.sku?.includes(sku))
    // );

    // logInfo('elastic Req ', {reqBody,responseStatus: response.status,missingSkus}, xHeaderToken);
    flashSale = response?.data?.flashSale;
    newFlashSale = response?.data?.newFlashSale;
    
    productArr = hits?.map(product => ({
      ...product,
      objectID: String(product.objectID),
      isGwp: checkIsGwp({
        enrich: product?.enrich,
        storeId,
        xHeaderToken,
      }),
    })) || [];

  } catch (error) {
    // console.log(logPrefix, "Error in the fn getProducts - Axios Error", error?.message);
    if (axios.isAxiosError(error)) {
      // console.log(logPrefix, "Error in the fn getProducts - Axios isAxiosError Error",error,error?.message);
      errorMessage = `Axios Error: ${error.message}`;
      // logError(error, 'fn getProducts - Axios Error', {
      //   getProductEndpoint,
      //   reqObj: reqBody,
      //   status: error.response?.status,
      //   data: error.response?.data,
      // });
    } else {
      errorMessage = 'Unexpected error occurred';
      // logError(error, 'fn getProducts - Unexpected Error', { getProductEndpoint, reqObj: reqBody });
    }
  }
  
  return { productArr, flashSale, errorMessage, newFlashSale };
};
