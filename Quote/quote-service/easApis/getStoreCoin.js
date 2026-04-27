const axios = require("axios");
const { upsertQuote } = require("../helpers/upsertQuote");
const { logError } = require("../helpers/utils");
// const { logInfo, logger } = require("../helpers/utils");
const { CHECK_REDEEM_COIN_ON_ORDER } = require("./easConstants");
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const cache = require('memory-cache');
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(",")?.[0];
const { setStyliCashBurnItemWise } = require("../helpers/styliCash");

const getStoreCoinBalance = async ({
	customerId,
	quote,
	statusCoinApllied,
	xHeaderToken,
}) => {
	
	try {
		const earnRequest = {
			customerId: customerId,
			storeId: quote.storeId,
			orderValue: quote.subtotalWithDiscount,
			status: statusCoinApllied ? statusCoinApllied : 0,
		};
		const earnRequestHeader = {
			headers: {
				"Content-Type": "application/json",
				"authorization-token": internalAuthToken,
			},
		};
		const response = await axios.post(
			`${process.env.EAS_URL}${CHECK_REDEEM_COIN_ON_ORDER}`,
			earnRequest,
			earnRequestHeader
		);
		return response;
	} catch (error) {
		if (error.response) {
			// logger.error(`getStoreCoinBalance: Error fetching coins for customer ${customerId} - Status: ${error.response.status}, Message: ${error.message}`);
			// logError(
			// 	{
			// 		message: error.message,
			// 		status: error.response.status,
			// 		data: error.response.data,
			// 	},
			// 	"Error coin fetch:",
			// 	xHeaderToken
			// );
		} else {
			logError({ message: error.message }, "Error coin fetch else:", xHeaderToken);
		}
		return null;
	}
};
exports.processCoinCalculations = async ({
	storeId,
	quote,
	collection,
	upsert,
	statusCoinApllied,
	xHeaderToken,
}) => {
	try {
		const configCacheKey = process.env.CONSUL_KEY;
		const configCacheResponse = cache.get(configCacheKey);
		if(!configCacheResponse?.styliCoin){
			delete quote['coinDiscountData'];
			quote = setStyliCashBurnItemWise(quote, 0);
			if (upsert) await upsertQuote({ storeId, quote, collection });
			return quote;
		}
		if (!quote.customerId || !quote?.storeId){
			// logInfo('EAS subtotalWithDiscount Null: ', '', quote);
			return quote;
		}

		const response = await getStoreCoinBalance({
			customerId: quote.customerId,
			quote,
			statusCoinApllied,
			xHeaderToken,
		});
		const discountObject = response?.data?.response;
		if (response?.data && response?.data?.code == 200) {
			quote.coinDiscountData = discountObject;
			quote.coinDiscountData.statusCoinApllied =
			(quote.coinDiscountData?.statusMinValueSatisfy == 1 &&
				quote.coinDiscountData.statusCoinApllied == 1)
				? 1
				: 0;
			quote = setStyliCashBurnItemWise(quote, statusCoinApllied);
			if (upsert) await upsertQuote({ storeId, quote, collection });
			return quote;
		}

		return quote;
	} catch (e) {
		// logError(e, "Error prcessing eas response : ", xHeaderToken);
		// logger.error(`processCoinCalculations: Error fetching EAS response - ${e.message}`);
	}
};
