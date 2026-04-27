const { BRAZE_LOG_CUSTOM_EVENT } = require('../constants/brazeEndpoints');
const axios = require('axios');
const { BRAZE_AUTH_TOKEN } = process.env;

exports.logBrazeCustomEvent = async (req, res) => {
  try {
    if (BRAZE_LOG_CUSTOM_EVENT) {
      if (process.env?.REGION?.toUpperCase() === 'IN') {
        console.log('Braze Disabled for region IN');
        return false;
      }
      console.log('Braze Request ', JSON.stringify(req));
      const response = await axios.post(
        BRAZE_LOG_CUSTOM_EVENT.replace(/'/g, ''),
        req,
        {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${BRAZE_AUTH_TOKEN}` || ''
          }
        }
      );
      console.log('Braze response ', response);
    }
  } catch (e) {
    global.logError(`Braze response Error ${e.message}`);
  }
};
