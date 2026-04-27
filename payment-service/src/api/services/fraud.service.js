const FRAUD_CUSTOMER_KEY = 'fraud_customers';
const _ = require('lodash');
const { customerCollection } = require('../../config/couchbase');
const logger = require('../../config/logger');
const mongoInit = require('../../config/mongoInit');
const redisClient = require('../../config/redis');
const crypto = require('node:crypto');

/**
 * Generate cache key for fraud check using SHA-256 hash
 * Uses SHA-256 instead of MD5 for security in this sensitive fraud detection context
 * @param {*} email
 * @param {*} phone
 * @returns
 */
const getFraudCacheKey = (email, phone) => {
  const keyData = `${email || ''}_${phone || ''}`;
  // Use SHA-256 instead of MD5 for security in fraud detection context
  const hash = crypto.createHash('sha256').update(keyData).digest('hex');
  return `fraud_check:${hash}`;
};

/**
 * Get cached fraud customer from Redis if available
 * @param {string} email
 * @param {string} phone
 * @returns {Promise<Object|null>}
 */
const getCachedFraudCustomer = async (email, phone) => {
  const cacheKey = getFraudCacheKey(email, phone);
  logger.info(`### enablePaymentOptimizations ### checkFraudCustomer ### Checking Redis cache with key: ${cacheKey}`);
  const cachedResult = await redisClient.get(cacheKey);

  if (!cachedResult) {
    logger.info(`### enablePaymentOptimizations ### checkFraudCustomer ### Cache miss, querying database`);
    return null;
  }

  const parsed = JSON.parse(cachedResult);
  const result = parsed === 'null' ? null : parsed;
  logger.info(
    `### enablePaymentOptimizations ### checkFraudCustomer ### Cache hit, result: ${
      result ? 'fraud customer found' : 'null'
    }`
  );
  return result;
};

/**
 * Query fraud customer from MongoDB
 * @param {string} email
 * @param {string} phone
 * @returns {Promise<Object|null>}
 */
const queryFraudCustomerFromMongo = async (email, phone) => {
  const db = mongoInit.getDb();
  const fraudCustArray = await db
    .collection('fraud_customers')
    .find({
      $or: [{ email: email }, { phone: phone }]
    })
    .toArray();
  const fraudCust = fraudCustArray?.length ? fraudCustArray[0] : null;
  logger.info(
    `### enablePaymentOptimizations ### checkFraudCustomer ### MongoDB query result: ${
      fraudCust ? 'fraud customer found' : 'no fraud customer'
    }`
  );
  return fraudCust;
};

/**
 * Query fraud customer from Couchbase
 * @param {string} email
 * @param {string} phone
 * @returns {Promise<Object|null>}
 */
const queryFraudCustomerFromCouchbase = async (email, phone) => {
  const custColl = await customerCollection();
  const custObj = await custColl.get(FRAUD_CUSTOMER_KEY);
  const values = custObj?.content ? Object.values(custObj.content) : [];
  const fraudCustArray = values.filter(cust => _.toUpper(cust.email) === _.toUpper(email) || cust.phone === phone);
  const fraudCust = fraudCustArray ? fraudCustArray[0] : null;
  logger.info(
    `### enablePaymentOptimizations ### checkFraudCustomer ### Couchbase query result: ${
      fraudCust ? 'fraud customer found' : 'no fraud customer'
    }`
  );
  return fraudCust;
};

/**
 * Query fraud customer from database (MongoDB or Couchbase)
 * @param {string} email
 * @param {string} phone
 * @param {boolean} fetchDataFromMongo
 * @returns {Promise<Object|null>}
 */
const queryFraudCustomerFromDatabase = async (email, phone, fetchDataFromMongo) => {
  if (fetchDataFromMongo) {
    return await queryFraudCustomerFromMongo(email, phone);
  }
  return await queryFraudCustomerFromCouchbase(email, phone);
};

/**
 * Cache fraud customer result in Redis
 * @param {string} email
 * @param {string} phone
 * @param {Object|null} fraudCust
 * @returns {Promise<void>}
 */
const cacheFraudCustomerResult = async (email, phone, fraudCust) => {
  const cacheKey = getFraudCacheKey(email, phone);
  const ttl = fraudCust ? 600 : 300; // 10 min for fraud, 5 min for non-fraud
  logger.info(`### enablePaymentOptimizations ### checkFraudCustomer ### Caching result with TTL: ${ttl} seconds`);
  await redisClient.set(cacheKey, JSON.stringify(fraudCust || 'null'), 'EX', ttl);
};

/**
 * Validate If the customer email and phone is marked as Fraud
 * @param {*} email
 * @param {*} phone
 * @returns
 */
const checkFraudCustomer = async (email, phone) => {
  try {
    const paymentMethods = globalThis.paymentMethods || {};
    const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)

    logger.info(
      `### enablePaymentOptimizations ### checkFraudCustomer ### enablePaymentOptimizations: ${enablePaymentOptimizations}, email: ${
        email || 'empty'
      }, phone: ${phone || 'empty'}`
    );

    // Check Redis cache first for faster response (if optimizations enabled)
    if (enablePaymentOptimizations) {
      const cachedResult = await getCachedFraudCustomer(email, phone);
      if (cachedResult !== null) {
        return cachedResult;
      }
    }

    // If not in cache, query database
    const { fetchDataFromMongo = true } = paymentMethods;
    logger.info(
      `### enablePaymentOptimizations ### checkFraudCustomer ### Querying database, fetchDataFromMongo: ${fetchDataFromMongo}`
    );
    const fraudCust = await queryFraudCustomerFromDatabase(email, phone, fetchDataFromMongo);

    // Cache the result with TTL (if optimizations enabled)
    if (enablePaymentOptimizations) {
      await cacheFraudCustomerResult(email, phone, fraudCust);
    }

    return fraudCust;
  } catch (error) {
    logger.error(`Error in Finding Fraud customer. Email : ${email}. Error : `, error);
    return null;
  }
};

exports.restrictFraudCustomerPayments = async (quote, methods) => {
  const customerId = quote?.customerId;
  const isGuestUser = !customerId || customerId === '' || (typeof customerId === 'string' && customerId.trim() === '');

  if (isGuestUser) {
    logger.info(
      `### enablePaymentOptimizations ### restrictFraudCustomerPayments for Quote: ${quote?.id} ### Skipping fraud check for guest user (customerId is empty/blank)`
    );
    return methods;
  }

  const quoteAddrs = quote.quoteAddress;
  logger.info(
    `### enablePaymentOptimizations ### restrictFraudCustomerPayments for Quote: ${quote?.id}, customerId: ${customerId} ### Checking fraud for email: ${quote.customerEmail}, phone: ${quoteAddrs?.mobileNumber}`
  );
  const fraudCust = await checkFraudCustomer(quote.customerEmail, quoteAddrs?.mobileNumber);

  if (fraudCust && fraudCust?.blocked_payments?.length > 0 && fraudCust?.forward) {
    logger.info(
      `### enablePaymentOptimizations ### restrictFraudCustomerPayments for Quote: ${
        quote?.id
      } ### Fraud customer detected, blocking payments: ${JSON.stringify(fraudCust.blocked_payments)}`
    );
    fraudCust.blocked_payments.forEach(paymt => {
      methods = methods.filter(p => !_.startsWith(p, paymt));
    });
  } else {
    logger.info(
      `### enablePaymentOptimizations ### restrictFraudCustomerPayments for Quote: ${quote?.id} ### No fraud restrictions applied`
    );
  }
  return methods;
};
