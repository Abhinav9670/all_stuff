const { logError } = require('../helpers/utils.js');
// const { logger } = require('../helpers/utils.js');
const { processAbandonedCart,abandonCartDetails } = require('../helpers/webhook/abandonedCart.js');

exports.processAbandonedCart = async (req, res) => {
  try {
    const result = await processAbandonedCart(req);
    // logger.info(`processAbandonedCart: Processing completed with result: ${JSON.stringify(result)}`);
    if (result) {
      res.status(200);
      res.json({
        status: true,
        statusCode: 200,
        statusMsg: "Getting quote data .",
        data: result
      });
    } else {
      throw new Error("Could not process abandonded cart.");
    }
  } catch (e) {
    res.status(500);
    // logError(e, `Error in processing abandoed cart`);
    res.json({
      status: false,
      statusCode: 500,
      statusMsg: e.message,
    });
  }
};

exports.handleAbandonCartDetails = async (req, res) => {
  try {
    const result = await abandonCartDetails(req, res);
    if (!result) {
      return res.status(404).json({
        status: false,
        statusCode: 404,
        statusMsg: "No abandoned cart details found.",
      });
    }
    res.status(200).json({
      status: true,
      statusCode: 200,
      statusMsg: "Getting product detail.",
      data: result,
    });
  } catch (e) {
    // logError(e, `Error in processing abandonCartDetails`);
    res.status(500).json({
      status: false,
      statusCode: 500,
      statusMsg: e.message,
    });
  }  
};