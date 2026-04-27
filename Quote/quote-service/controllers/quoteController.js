const _ = require('lodash');
const fetchQuote = require('../helpers/fetchQuote');
const {
  addProductToQuote,
  validateAddProductReq
} = require('../helpers/addToQuote');
const { removeItemFromQuote } = require('../helpers/removeItem');
const { updateItemQty } = require('../helpers/updateQty');
const { upsertQuote } = require('../helpers/upsertQuote');
const { pushCurofyAnalytics } = require('../helpers/analytics');
const { fetchPromoResponse } = require('../promoApis/v6/processPromo');
const { getStoreCreditBalance } = require('../javaApis/getStoreCreditBalance');
const {
  getStoreConfig,
  logError,
  logInfo,
} = require('../helpers/utils');
const { fetchExternalQuote } = require('../helpers/fetchExternalQuote');
const { collection, initcluster, couchbase } = require('../config/couchbase.js');
const { uuidCheckFailed, uuidError } = require('../helpers/validateToken');
const cache = require('memory-cache');
const { clearSesssion } = require('../helpers/payments/paymentService');
const { isFlashSaleActive, findFlashProductsPerUser, handleSkuUpdate } = require('../helpers/flashUtils');

exports.getQuoteUnprocessed = async function ({ req, res }) {
  const { uuid, body } = req;
  const { storeId } = body;
  const quoteId = String(body?.quoteId || "");
  const customerId = String(body?.customerId || "");

  const quoteCollection = await collection();
  const cluster = await initcluster();
  let quote;
  if (!customerId) {
    quote = await fetchQuote.fetchQuote({
      identifier: quoteId,
      storeId,
      collection: quoteCollection,
      cluster,
      type: 'guest',
      res
    });
  } else {
    quote = await fetchQuote.fetchQuote({
      identifier: customerId,
      storeId,
      collection: quoteCollection,
      cluster,
      type: 'customer',
      res
    });
  }
  if (!quote) {
    res.status(200);
    res.json({
      status: false,
      statusCode: '202',
      statusMsg: 'quote not found!'
    });
    return;
  }

  if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

  return res.status(200).json({
    status: true,
    statusCode: '200',
    statusMsg: 'quote found!'
  });
};

exports.getQuoteNew = async ({ req, res }) => {
  const quoteId = String(req.body?.quoteId || "");
  const key = `quote_${quoteId}`;
  const collectionStr = await collection();
  const apm = global?.apm;
  let span;
  try {
    let quote = {};
    span = apm?.startSpan('CB: KV getQuoteNew', 'db', 'couchbase', 'get');
    if (span) {
      span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    const quoteData = await collectionStr.get(key);
    quote = quoteData?.content;
    res.status(200);
    res.json({
      status: true,
      statusCode: '200',
      statusMsg: 'success',
      // quote: JSON.stringify(quote)
      quote
    });
    return;
  } catch (e) {
    let replicaSpan;
    try {
      let replicaData = {};
      replicaSpan = apm?.startSpan('CB: KV Replica getQuoteNew', 'db', 'couchbase', 'get');
      if (replicaSpan) {
        replicaSpan.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
      }
      replicaData = await collectionStr.getAnyReplica(key);
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: e.message,
        replicaData: JSON.stringify(replicaData)
      });
      return;
    } catch (e) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: e.message
      });
      return;
    } finally {
      if (replicaSpan) replicaSpan.end();
    }
  } finally {
    if (span) span.end();
  }
};

exports.addToQuote = async ({ req, res }) => {
  const { headers } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const logPrefix = `addToQuote:::${new Date().toISOString()}`;
  try {
    const connStr = await collection();
    const clusterObj = await initcluster();
    if (!connStr || !clusterObj) {
      // console.log(logPrefix, "Couchbase connection is missing : in Add to Quote API : ")
    }
    let response = {};
    const { body, uuid } = req;
    const {
      storeId,
      source,
      addToQuoteProductsRequests
    } = body;
    // logInfo(logPrefix, ` In Add TO Quote storeId, ${storeId}`);
    // logInfo(logPrefix, ` In Add TO Quote source, ${source}`);
    // logInfo(logPrefix, ` In Add TO Quote addToQuoteProductsRequests, ${addToQuoteProductsRequests}`);
    const quoteId = String(body?.quoteId || "");
    const customerId = String(body?.customerId || "");
    // logInfo(logPrefix, ` In Add TO Quote customerId, ${customerId}`);
    // logInfo(logPrefix, ` In Add TO Quote quoteId, ${quoteId}`);
    if (validateAddProductReq({ addToQuoteProductsRequests })) {
      // logInfo(logPrefix, `In validateAddProductReq`);
      let quote;
      if (!customerId) {
        // logInfo(logPrefix, `In customerId is not there :`)
        quote = await fetchQuote.fetchQuote({
          identifier: quoteId,
          storeId,
          collection: connStr,
          cluster: clusterObj,
          type: 'guest',
          res
        });
      } else {
        // logInfo(logPrefix, `In customerId is there : ${customerId}`)
        quote = await fetchQuote.fetchQuote({
          identifier: customerId,
          storeId,
          collection: connStr,
          cluster: clusterObj,
          type: 'customer',
          res
        });
      }
      if (!quote && quoteId) {
        // logInfo(logPrefix, `quote is not there:::`);
        res.status(200);
        res.json({
          status: false,
          statusCode: '202',
          statusMsg: 'quote not found!'
        });
        return;
      }
      if (quote) {
        // logInfo(logPrefix, `quote is there in addToQuoteProductsRequests`);
        const skuList = quote?.skus || [];
        const sku = addToQuoteProductsRequests.map(addProduct => { return addProduct.sku });
        // logInfo('sku', sku);
        if (sku?.length > 0) {
          skuList.push(sku[0]);
        }
        if (skuList && skuList?.length > 0) {
          quote.skus = skuList;
        }
        const websiteId = Number(
          getStoreConfig(storeId, 'websiteId') || 0
        );
        let webSiteArray = []
        webSiteArray.push(websiteId);
        quote.websiteId = webSiteArray;
      }


      const baseConfig = cache.get('baseConfig');
      // logInfo('baseConfig', baseConfig);
      if (uuid && baseConfig?.uuid_validation) {
        // logInfo(logPrefix, `In the baseConfig `);
        // logInfo('UUID present in JWT', 'UUID present in JWT', xHeaderToken);
        if (quote?.uuid && quote?.uuid != uuid)
          return res.status(400).json({
            status: false,
            statusCode: '400',
            statusMsg: 'JWT uuid absent/mismatch!'
          });
      }
      const itemsCount = quote?.quoteItem?.length || 0;
      if (
        process.env.MAX_BAG_COUNT &&
        itemsCount + 1 > Number(process.env.MAX_BAG_COUNT)
      ) {
        // logInfo(
        //   `Maximum limit of products reached `,
        //   `${quote?.id} item count : ${itemsCount}`,
        //   xHeaderToken
        // );

        return res.status(500).json({
          status: false,
          statusCode: '500',
          statusMsg: 'Maximum limit of products reached'
        });
      }
      const matchedSku = quote?.quoteItem?.find(qItem => addToQuoteProductsRequests.some(req => req.sku === qItem.sku)) || {};
      if (Object.keys(matchedSku).length > 0) {
        quote = await handleSkuUpdate({ matchedSku, quote, xHeaderToken, res })
      }
      else {
        const addToQuoteResponse = await addProductToQuote({
          storeId,
          source,
          quote,
          customerId,
          addToQuoteProductsRequests,
          collection: connStr,
          res,
          xHeaderToken
        });
        // logInfo(logPrefix, `In the addToQuoteResponse  ${addToQuoteResponse}`);
        if (addToQuoteResponse?.error) {
          // logInfo(
          //   logPrefix,
          //   addToQuoteProductsRequests,
          //   'Product not found : ',
          //   xHeaderToken
          // );
          res.status(200).json(addToQuoteResponse.error);
          return '';
        }
        quote = addToQuoteResponse.quote;

        // logInfo(logPrefix, `In the addToQuoteResponse after the response quote :: ${quote}`);

        if (addToQuoteResponse?.isGift) {
          const toRemoveSkuObj = quote?.quoteItem?.find(
            item => item.isGift && item.sku !== addToQuoteProductsRequests[0]?.sku
          );
          if (toRemoveSkuObj && toRemoveSkuObj.sku) {
            // console.log("GWP missing debug log::: remove item from `addToQuote`", toRemoveSkuObj, "quoteId", quote?.id);
            const removeQuoteResponse = await removeItemFromQuote({
              quote,
              skus: [toRemoveSkuObj.sku],
              xHeaderToken
            });
            quote = removeQuoteResponse.quote;
          }
        }
      }
      if (uuid && !quote?.uuid) {
        // logInfo(
        //   'UUID not present in quote',
        //   'saving UUID to quote',
        //   xHeaderToken
        // );
        quote.uuid = uuid;
      }
      const upsertResponse = await upsertQuote({
        storeId,
        quote,
        collection: connStr,
        xHeaderToken
      });
      if (upsertResponse) {
        response = {
          customerId,
          error: null,
          quoteId: String(quote.id),
          status: true,
          statusCode: '200',
          statusMsg: 'Success!'
        };

        return res.status(200).json(response);
      }
    } else {
      res.status(200);
      res.json({
        status: false,
        statusCode: '201',
        statusMsg: 'Invalid request'
      });
      return '';
    }
  } catch (e) {
    // logError(e, 'Error addToQuote : ', logPrefix, xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.deleteItem = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const logPrefix = `deleteItem:::${new Date().toISOString()}`;
  try {
    const collectionObj = await collection();
    const clusterConn = await initcluster();
    if (!collectionObj || !clusterConn) {
      // console.log(logPrefix, "Couchbase connection is missing : in Delete Item API : ");
    }
    const { storeId, skus } = body;
    const quoteId = String(body?.quoteId || "");
    const customerId = String(body?.customerId || "");
    let quote;
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: collectionObj,
        cluster: clusterConn,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: collectionObj,
        cluster: clusterConn,
        type: 'customer',
        res
      });
    }

    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    if (storeId && skus?.length) {
      // console.log("GWP missing debug log::: remove item from `deleteItem`", skus, "quoteId", quote?.id);
      const removeQuoteResponse = await removeItemFromQuote({
        quote,
        skus,
        res,
        xHeaderToken
      });

      if (removeQuoteResponse?.error) {
        res.status(200).json(removeQuoteResponse.error);
        return '';
      }

      quote = removeQuoteResponse?.quote;

      await upsertQuote({ storeId, quote, collection: collectionObj, xHeaderToken });
      const response = {
        status: true,
        statusCode: '200',
        statusMsg: 'Success',
        quoteId: String(quote?.id),
        customerId: String(quote?.customerId || ''),
        error: null
      };
      return res.status(200).json(response);
    } else {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'Invalid request'
      });
      return '';
    }
  } catch (e) {
    // logError(e, 'Error deleteing item : ', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.updateQty = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    let response;
    const connObj = await collection();
    const clusterRes = await initcluster();
    const { storeId, quantity: requestedQty, sku } = body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    let quote;
    const logPrefix = `updateQty quote:::${new Date().toISOString()}`;

    if (!connObj || !clusterRes) {
      // console.log(logPrefix, "couchbase connection missing in updateQty:")
    }
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: connObj,
        cluster: clusterRes,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: connObj,
        cluster: clusterRes,
        type: 'customer',
        res
      });
    }

    if (!quote) {
      return res.status(200).json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
    }
    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    if (storeId && requestedQty && sku) {
      const quoteItemObj = _.find(quote?.quoteItem, { sku });
      if (_.isEmpty(quoteItemObj)) {
        return res.status(200).json({
          status: false,
          statusCode: '500',
          statusMsg: 'product not found'
        });
      }

      if (quoteItemObj?.isGift) {
        return res.status(400).json(
          {
            status: false,
            statusCode: '400',
            statusMsg: 'Operation not allowed on gift'
          }
        )
      }

      if (isFlashSaleActive(quote.flashSale)) {
        const { capPerUser, flashsaleId: flashSaleId, stockCapExhausted } =
          quoteItemObj?.flashConfig || {};
        const parentSku = quoteItemObj.parentSku;

        const productsPurchased = await findFlashProductsPerUser({
          quote,
          parentSku,
          flashSaleId,
        });

        const productsInQuote = quote.quoteItem
          ?.filter((item) => item.parentSku === quoteItemObj.parentSku && item.sku !== sku)
          .reduce((acc, item) => acc + item.qty, 0);

        const productTotal = productsPurchased + productsInQuote + requestedQty;

        if (productTotal > capPerUser && Number(stockCapExhausted) !== 1) {
          return res.status(400).json({
            status: false,
            statusCode: "400",
            statusMsg: "Flashsale restriction for product.",
          });
        }
      }
      quote = await updateItemQty({ quote, requestedQty, sku, xHeaderToken });
      const itemsQty = quote.quoteItem?.reduce((totalQty, quoteItem) => {
        return totalQty + Number(quoteItem.qty);
      }, 0);

      quote.itemsCount = quote?.quoteItem?.length;
      quote.itemsQty = itemsQty;
      const upsertResponse = await upsertQuote({
        storeId,
        quote,
        collection: connObj,
        xHeaderToken
      });
      if (upsertResponse) {
        response = {
          status: true,
          statusCode: '200',
          statusMsg: 'Success',
          quoteId: String(quote.id),
          customerId: String(quote.customerId),
          error: null
        };
      }
      return res.status(200).json(response);
    } else {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'Invalid request'
      });
      return '';
    }
  } catch (e) {
    // logError(e, 'Error updating qty : ', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.changeSize = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    const { storeId, skuToDelete, addRequest } = req.body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    const logPrefix = `changeSize:::${new Date().toISOString()}`;
    let quote;
    let response;
    const quoteRes = await collection();
    const resultCluster = await initcluster();
    if (!quoteRes || !resultCluster) {
      // console.log(logPrefix, "couchbase connection missing in changeSize:");
    }
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: quoteRes,
        cluster: resultCluster,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: quoteRes,
        cluster: resultCluster,
        type: 'customer',
        res
      });
    }

    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    const quoteItemObj = _.find(quote?.quoteItem, { sku: String(skuToDelete) });

    if (quoteItemObj?.isGift) {
      return res.status(400).json(
        {
          status: false,
          statusCode: '400',
          statusMsg: 'Operation not allowed on gift'
        }
      )
    }

    const parentSkuToDelete = quoteItemObj?.parentSku;
    let { warehouseId = "", fulfillmentMode = "" } = addRequest || {};

    if (
      storeId &&
      addRequest &&
      typeof addRequest === 'object' &&
      parentSkuToDelete === addRequest?.parentSku
    ) {
      // console.log("GWP missing debug log::: remove item from `changeSize`", skuToDelete, "quoteId", quote?.id);
      const removeQuoteResponse = await removeItemFromQuote({
        skus: [skuToDelete],
        quote,
        xHeaderToken
      });
      if (removeQuoteResponse?.error) {
        res.status(200).json(removeQuoteResponse.error);
        return '';
      }
      quote = removeQuoteResponse.quote;
      const removedItem = removeQuoteResponse?.removedItem || {};
      const {
        caSource,
        caSourceType,
        caSourceValue,
        caBannerPromoName,
        caSearchId
      } = removedItem;

      const addToQuoteResponse = await addProductToQuote({
        quote,
        storeId,
        customerId,
        addToQuoteProductsRequests: [
          {
            caSource,
            caSourceType,
            caSourceValue,
            caBannerPromoName,
            caSearchId,
            warehouseId,
            fulfillmentMode,
            ...addRequest
          }
        ],
        res,
        xHeaderToken
      });
      if (addToQuoteResponse?.error) {
        // logInfo(addRequest, 'Product not found : ', xHeaderToken);
        res.status(200).json(addToQuoteResponse.error);
        return '';
      }

      quote = addToQuoteResponse?.quote;

      const itemsQty = quote.quoteItem?.reduce((totalQty, quoteItem) => {
        return totalQty + Number(quoteItem.qty);
      }, 0);

      quote.itemsCount = quote?.quoteItem?.length;
      quote.itemsQty = itemsQty;

      const upsertResponse = await upsertQuote({
        storeId,
        quote,
        collection: quoteRes,
        xHeaderToken
      });
      if (upsertResponse) {
        response = {
          status: true,
          statusCode: '200',
          statusMsg: 'Success',
          quoteId: String(quote.id),
          customerId: String(quote.customerId || ''),
          error: null
        };
      }
      return res.status(200).json(response);
    } else {
      return res.status(200).json({
        status: false,
        statusCode: '202',
        statusMsg: 'Invalid request'
      });
    }
  } catch (e) {
    // logError(e, 'Error changing size : ', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.migrateQuote = async ({ req, res }) => {
  const { headers, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const { quoteList } = body;
  const customerId = String(body.customerId || "");
  let response = {};
  try {
    const connectionObj = await collection();
    const resCluster = await initcluster();
    if (typeof quoteList === 'object' && customerId) {
      for (const quoteIndex in quoteList) {
        const quoteData = quoteList[quoteIndex];
        const { storeId, quoteId } = quoteData;
        const guestQuote = await fetchQuote.fetchQuote({
          identifier: quoteId,
          storeId,
          collection: connectionObj,
          cluster: resCluster,
          type: 'guest',
          res
        });

        const existingQuote = await fetchQuote.fetchQuote({
          identifier: customerId,
          storeId,
          collection: connectionObj,
          cluster: resCluster,
          type: 'customer',
          res
        });

        if (guestQuote) {
          const addToQuoteReq = guestQuote?.quoteItem?.map(quoteItem => {
            return {
              ...quoteItem,
              quantity: quoteItem.qty,
              overrideQuantity: false
            };
          });

          const addToQuoteResponse = await addProductToQuote({
            quote: existingQuote,
            storeId,
            customerId,
            addToQuoteProductsRequests: addToQuoteReq,
            res,
            fromMigrate: true,
            couponCode: guestQuote.couponCode,
            xHeaderToken
          });

          if (addToQuoteResponse?.error) {
            // logInfo(addToQuoteReq, 'Product not found : ', xHeaderToken);

            res.status(200).json(addToQuoteResponse.error);
            return '';
          }

          let quote = addToQuoteResponse?.quote;

          if (addToQuoteResponse?.isGift) {
            const toAddskus = addToQuoteReq?.map(item => item.sku);
            const toRemoveSkuObj = quote?.quoteItem?.find(
              item => item.isGift && !toAddskus.includes(item.sku)
            );
            if (toRemoveSkuObj && toRemoveSkuObj.sku) {
              // console.log("GWP missing debug log::: remove item from `migrateQuote`", toRemoveSkuObj, "quoteId", quote?.id);
              const removeQuoteResponse = await removeItemFromQuote({
                quote,
                skus: [toRemoveSkuObj.sku],
                xHeaderToken
              });
              quote = removeQuoteResponse.quote;
            }
          }

          guestQuote.isActive = 0;
          const guestUpsertResponse = await upsertQuote({
            storeId,
            quote: guestQuote,
            expiration: process.env.COUCHBASE_QUOTE_DOC_EXPIRY,
            collection: connectionObj,
            xHeaderToken
          });

          const upsertResponse = await upsertQuote({
            storeId,
            quote,
            collection: connectionObj,
            xHeaderToken
          });
          if (upsertResponse && guestUpsertResponse) {
            response = {
              status: true,
              statusCode: '200',
              statusMsg: 'Success',
              quoteId: String(quote.id),
              customerId: String(quote.customerId || ''),
              error: null
            };
          }
          return res.status(200).json(response);
        } else {
          res.status(200);
          res.json({
            status: false,
            statusCode: '202',
            statusMsg: 'Requested quote not available'
          });
          return '';
        }
      }
    } else {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'Invalid request'
      });
      return '';
    }
  } catch (e) {
    // logError(e, 'Error migrating quote  : ', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.disableQuote = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    const { storeId, retryPayment = false } = body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    let quote;
    const collectionOj = await collection();
    const clusterCon = await initcluster();
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: collectionOj,
        cluster: clusterCon,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: collectionOj,
        cluster: clusterCon,
        type: 'customer',
        res
      });
    }
    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    pushCurofyAnalytics({ quote, xHeaderToken });

    quote.isActive = 0;
    quote.retryPayment = retryPayment;
    const result = await upsertQuote({
      storeId,
      quote,
      collection: collectionOj,
      expiration: process.env.COUCHBASE_QUOTE_DOC_EXPIRY,
      xHeaderToken
    });
    // logInfo('quote Upsert response in disableQuote', result, xHeaderToken);

    return res.status(200).json({
      status: true,
      statusCode: '200',
      statusMsg: 'success, quote disabled: ' + String(quote.id)
    });
  } catch (e) {
    // logError(e, 'Error disabling quote', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.enableQuote = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const apm = global?.apm;
  let span;
  try {
    const quoteResObj = await collection();
    const { storeId, retryPayment = false } = body;
    const quoteId = String(body.quoteId || "");
    let quote;
    let quoteData;
    try {
      span = apm?.startSpan('CB: KV enableQuote', 'db', 'couchbase', 'get');
      if (span) {
        span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
      }
      quoteData = await quoteResObj.get(`quote_${quoteId}`);
    } finally {
      if (span) span.end();
    }
    quote = quoteData?.content;

    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    quote.isActive = 1;
    quote.retryPayment = retryPayment;
    await upsertQuote({ storeId, quote, collection: quoteResObj, xHeaderToken });
    await clearSesssion(quote, headers);

    return res.status(200).json({
      status: true,
      statusCode: '200',
      statusMsg: 'success, quote enabled: ' + String(quote.id),
      quoteId,
      customerId: quote.customerId
    });
  } catch (e) {
    // logError(e, 'Error enabling quote', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.applyCoupon = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    let { storeId, coupon } = body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    const promoCustomErrorCodes = process.env.PROMO_CUSTOM_ERROR_CODES;

    let quote;
    const quoteColl = await collection();
    const quoteCluster = await initcluster();
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: quoteColl,
        cluster: quoteCluster,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: quoteColl,
        cluster: quoteCluster,
        type: 'customer',
        res
      });
    }
    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    if (quote.couponCode || coupon == undefined) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '203',
        statusMsg: 'coupon already applied or not provided!'
      });
      return;
    }

    const response = await fetchPromoResponse({ quote, coupon, xHeaderToken });

    if (response?.data && response?.data?.code == 200) {
      const discountObject = response?.data?.response;
      for (const discount of discountObject.discounts) {
        if (discount.redeem_type == 'MANUAL') {
          const errorCode = discount.error_code;
          const errorMessage = discount.message;
          const couponCode = discount.coupon_code;
          if (errorCode == undefined) {
            quote.couponCode = couponCode;
            await upsertQuote({ storeId, quote, collection: quoteColl, xHeaderToken });
            return res.status(200).json({
              status: true,
              statusCode: '200',
              statusMsg:
                'success, coupon applied: ' + coupon + ' for quote: ' + quote.id
            });
          }
          return res.status(200).json({
            status: false,
            statusCode: promoCustomErrorCodes.includes(errorCode)
              ? '300'
              : errorCode,
            statusMsg: errorMessage
          });
        }
      }
    }

    //  logInfo(
    //   'inValid Promo Response',
    //   { invalidPromoResponse: response },
    //   xHeaderToken
    // );

    const errorCode = response?.data?.code;
    const errorMessage = response?.data?.message;
    return res.status(200).json({
      status: false,
      statusCode: promoCustomErrorCodes.includes(errorCode) ? '300' : errorCode,
      statusMsg: errorMessage
    });
  } catch (e) {
    // logError(e, 'Error applying quote', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.deleteCoupon = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    const { storeId } = req.body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    let quote;
    const collectionResult = await collection();
    const clusterResult = await initcluster();
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: collectionResult,
        cluster: clusterResult,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: collectionResult,
        cluster: clusterResult,
        type: 'customer',
        res
      });
    }
    if (!quote) {
      return res.status(200).json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    quote.couponCode = null;

    await upsertQuote({ storeId, quote, collection: collectionResult, xHeaderToken });

    return res.status(200).json({
      status: true,
      statusCode: '200',
      statusMsg: 'success, coupon removed for quote: ' + quote.id
    });
  } catch (e) {
    // logError(e, 'Error removing coupon', xHeaderToken);
    res.status(500);
    res.json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
    return {};
  }
};

exports.getCount = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    const resQuoteColl = await collection();
    const resQuoteCluster = await initcluster();
    const { storeId } = body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    let quote;
    let response;
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: resQuoteColl,
        cluster: resQuoteCluster,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: resQuoteColl,
        cluster: resQuoteCluster,
        type: 'customer',
        res
      });
    }

    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);
    const giftItems = quote?.quoteItem?.filter(item => item.isGift)?.length;
    response = {
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response: { itemsCount: quote?.itemsCount, itemsQty: quote?.itemsQty, giftItemCount: giftItems },
      error: null
    };
    return res.status(200).json(response);
  } catch (e) {
    // logError(e, 'Error getCount  : ', xHeaderToken);
    res.status(500);
    res.json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.getMetadata = async ({ req, res }) => {
  const { headers } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const apm = global?.apm;

  const quoteId = String(req?.query?.quoteId || '');
  const key = `quote_${quoteId}`;
  let quote = null;
  const meteDataCollection = await collection();
  let span;
  try {
    span = apm?.startSpan('CB: KV getMetadata', 'db', 'couchbase', 'get');
    if (span) {
      span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    const quoteData = await meteDataCollection.get(key);
    quote = quoteData?.content;
  } catch (e) {
    // logError(
    //   e,
    //   `${key} - error getting quote by key from acutal node`,
    //   xHeaderToken
    // );
    let replicaSpan;
    try {
      replicaSpan = apm?.startSpan('CB: KV Replica getMetadata', 'db', 'couchbase', 'get');
      if (replicaSpan) {
        replicaSpan.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
      }
      const replicaData = await meteDataCollection.getAnyReplica(key);
      quote = replicaData?.content;
    } catch (e) {
      // logError(
      //   e,
      //   `${key} - error getting quote by key from replica node`,
      //   xHeaderToken
      // );
    } finally {
      if (replicaSpan) replicaSpan.end();
    }
  } finally {
    if (span) span.end();
  }
  if (!quote) {
    return res.status(200).json({
      status: false,
      statusCode: '202',
      statusMsg: 'quote not found!'
    });
  }

  return res.status(200).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Success',
    quoteId: String(quoteId),
    response: { metadata: quote?.metadata },
    error: null
  });
};

exports.validate = async ({ req, res }) => {
  const { headers } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const apm = global?.apm;

  const quoteId = String(req?.query?.quoteId || '');
  const key = `quote_${quoteId}`;
  let quote = null;
  const validateCollection = await collection();
  let span;
  try {
    span = apm?.startSpan('CB: KV validate', 'db', 'couchbase', 'get');
    if (span) {
      span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    const quoteData = await validateCollection.get(key);
    quote = quoteData?.content;
  } catch (e) {
    // logError(
    //   e,
    //   `${key} - error getting quote by key from acutal node`,
    //   xHeaderToken
    // );
    let replicaSpan;
    try {
      replicaSpan = apm?.startSpan('CB: KV Replica validate', 'db', 'couchbase', 'get');
      if (replicaSpan) {
        replicaSpan.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
      }
      const replicaData = await validateCollection.getAnyReplica(key);
      quote = replicaData?.content;
    } catch (e) {
      // logError(
      //   e,
      //   `${key} - error getting quote by key from replica node`,
      //   xHeaderToken
      // );
    } finally {
      if (replicaSpan) replicaSpan.end();
    }
  } finally {
    if (span) span.end();
  }
  if (!quote) {
    return res.status(200).json({
      status: false,
      statusCode: '201',
      statusMsg: 'quote not found!'
    });
  }

  return res.status(200).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Success',
    quoteId: String(quoteId),
    response: { isValid: quote?.isActive },
    error: null
  });
};

exports.applyStoreCredit = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const token = headers['token'];
  const creditCollection = await collection();
  const creditCluster = await initcluster();
  try {
    const { storeId, amount } = body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    let quote;
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: creditCollection,
        cluster: creditCluster,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: creditCollection,
        cluster: creditCluster,
        type: 'customer',
        res
      });
    }
    if (!quote) {
      return res.status(200).json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    if (amount <= 0) {
      return res.status(200).json({
        status: false,
        statusCode: '203',
        statusMsg: 'Store credit value negative or zero!'
      });
    }

    const storeCreditObject = await getStoreCreditBalance({
      customerId: quote.customerId,
      storeId,
      token,
      xHeaderToken
    });
    if (storeCreditObject) {
      if (amount > storeCreditObject) {
        res.status(200);
        res.json({
          status: false,
          statusCode: '204',
          statusMsg: 'Requested amount is greater than store credit balance!'
        });
        return;
      }
    } else {
      res.status(200);
      res.json({
        status: false,
        statusCode: '205',
        statusMsg: 'Store credit not found for customer!'
      });
      return;
    }

    const currencyConversionRate = Number(
      getStoreConfig(storeId, 'currencyConversionRate') || 1
    );

    quote.amstorecreditUse = true;
    quote.amstorecreditBaseAmount = amount / currencyConversionRate;
    quote.amstorecreditAmount = amount;

    const result = await upsertQuote({
      storeId,
      quote,
      collection: creditCollection,
      xHeaderToken
    });
    // logInfo('quote Upsert response in applyStoreCredit', result, xHeaderToken);

    return res.status(200).json({
      status: true,
      statusCode: '200',
      statusMsg: 'success, store credit applied to quote: ' + quote.id
    });
  } catch (e) {
    // logError(e, 'Error applying store credit', xHeaderToken);
    res.status(500);
    res.json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
    return {};
  }
};

exports.deleteStoreCredit = async ({ req, res }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    const storeCollection = await collection();
    const storeCluster = await initcluster();
    let { storeId } = body;
    const customerId = String(body.customerId || "");
    let quote;
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: storeCollection,
        cluster: storeCluster,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: storeCollection,
        cluster: storeCluster,
        type: 'customer',
        res
      });
    }
    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    quote.amstorecreditUse = false;
    await upsertQuote({ storeId, quote, collection: storeCollection, xHeaderToken });

    return res.status(200).json({
      status: true,
      statusCode: 200,
      statusMsg: 'success, store credit removed from quote: ' + quote.id
    });
  } catch (e) {
    // logError(e, 'Error removing store credit', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.payment = async ({ req, res }) => {
  const { headers, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const apm = global?.apm;
  try {
    const paymentCollection = await collection();
    const paymentCluster = await initcluster();
    const { paymentCode } = body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    const availablePaymentMethods = JSON.parse(process.env.PAYMENT_METHODS);

    if (availablePaymentMethods.indexOf(paymentCode) < 0) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'invalid payment method!'
      });
      return;
    }

    if (customerId) {
      const query = `update quote set quotePayment.method = '${paymentCode}' where customerId='${customerId}' and isActive=1`;
      let span;
      try {
        span = apm?.startSpan('CB: Query payment', 'db', 'couchbase', 'query');
        if (span) {
          span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
        }
        const quoteupdateData = await paymentCluster.query(query);
        res.status(200);
        res.json({
          status: true,
          statusCode: '200',
          statusMsg: quoteupdateData
        });
        return;
      } finally {
        if (span) span.end();
      }
    } else {
      const key = `quote_${quoteId}`;
      let span;
      try {
        span = apm?.startSpan('CB: MutateIn payment', 'db', 'couchbase', 'mutateIn');
        if (span) {
          span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
        }
        const response = await paymentCollection.mutateIn(key, [
          couchbase.MutateInSpec.upsert('quotePayment.method', paymentCode)
        ]);
        res.status(200);
        res.json({
          status: true,
          statusCode: '200',
          statusMsg: response
        });
        return;
      } finally {
        if (span) span.end();
      }
    }
  } catch (e) {
    // logError(e, 'error updating payment', xHeaderToken);
    return res.status(200).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

//Deprecated
exports.payment2 = async ({ req, res }) => {
  const { headers, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    const quotePaymentColl = await collection();
    const quotePaymentCluster = await initcluster();
    const { storeId, paymentCode } = body;
    const quoteId = String(body.quoteId || "");
    const customerId = String(body.customerId || "");
    const availablePaymentMethods = JSON.parse(process.env.PAYMENT_METHODS);

    if (availablePaymentMethods.indexOf(paymentCode) < 0) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'invalid payment method!'
      });
      return;
    }

    let quote;
    if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection: quotePaymentColl,
        cluster: quotePaymentCluster,
        type: 'guest',
        res
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection: quotePaymentColl,
        cluster: quotePaymentCluster,
        type: 'customer',
        res
      });
    }

    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!'
      });
      return;
    }

    quote.quotePayment.method = paymentCode;
    quote.quotePayment.updatedAt = new Date();
    await upsertQuote({ storeId, quote, collection: quotePaymentColl, xHeaderToken });

    return res.status(200).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Success!'
    });
  } catch (e) {
    // logError(e, 'Error updating payment method', xHeaderToken);
  }
};

exports.migrateOldQuote = async ({ res, req, pool }) => {
  const { headers, body } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  try {
    const migrateCollection = await collection();
    let { quoteIds = null } = body;
    const customerId = String(body.customerId || "");
    if (
      (quoteIds && (!quoteIds?.length || typeof quoteIds !== 'object')) ||
      (!customerId && !quoteIds)
    ) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'Invalid request'
      });
      return '';
    }

    const upsertErrors = [];
    const apm = global?.apm;
    const response = await fetchExternalQuote({
      customerId,
      quoteIdArray: quoteIds,
      pool,
      xHeaderToken
    });
    const { formattingError, fetchError, formattedData } = response;
    if (formattedData && formattingError?.length < 1) {
      for (const qIndex in formattedData) {
        let span;
        try {
          const quote = formattedData[qIndex].quoteData;
          const key = `quote_${quote.id}`;
          span = apm?.startSpan('CB: Upsert migrateOldQuote', 'db', 'couchbase', 'upsert');
          if (span) {
            span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
          }
          await migrateCollection.upsert(key, quote);
        } catch (e) {
          upsertErrors.push(e.message);
        } finally {
          if (span) span.end();
        }
      }

      return res.status(200).json({
        status: true,
        statusCode: '200',
        statusMsg: 'success'
      });
    }
    return res.status(200).json({
      status: false,
      statusCode: '500',
      response: { formattingError, upsertErrors, fetchError }
    });
  } catch (e) {
    // logError(e, 'Error migrating single quote', xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: '500',
      statusMsg: e.message
    });
  }
};

exports.savePaymentMethodInQuote = async ({ req, res }) => {
  const apm = global?.apm;
  let span;
  try {
    const { body } = req;
    const customerId = String(body?.customerId || "");
    const { storeId } = body;
    const logPrefix = `savePPToQuote:::${new Date().toISOString()}`;
    const preferred_payment = body?.preferred_payment || "";

    const basicDataCluster = await initcluster();
    const quoteCollection = await collection();

    const quote = await fetchQuote.fetchQuote({
      identifier: customerId,
      storeId,
      collection: quoteCollection,
      cluster: basicDataCluster,
      type: "customer",
      res,
    });

    if (!quote?.id) {
      return res.status(404).json({ status: false, statusCode: "404", statusMsg: 'Quote not found' });
    }

    const key = `quote_${quote?.id}`;
    // logInfo(`${logPrefix} - quote key`, key);
    span = apm?.startSpan('CB: MutateIn savePaymentMethodInQuote', 'db', 'couchbase', 'mutateIn');
    if (span) {
      span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    const result = await quoteCollection.mutateIn(key, [
      couchbase.MutateInSpec.upsert("preferred_payment", preferred_payment)
    ]);

    if (result?.cas) {
      return res.status(200).json({ status: true, statusCode: "200", statusMsg: 'Preferred payment method saved successfully' });
    } else {
      return res.status(500).json({ status: false, statusCode: "500", statusMsg: 'Failed to save preferred payment method' });
    }
  } catch (e) {
    // logError(e, "got issue in saving preferred payment in quote ");
    return res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: e.message,
    });
  } finally {
    if (span) span.end();
  }
};