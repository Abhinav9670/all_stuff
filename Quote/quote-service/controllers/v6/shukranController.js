const _ = require("lodash");
const axios = require("axios");
const { client } = require('../../config/redis-config');
const { getAppConfigKey } = require("../../helpers/utils");
// const logger = require("../../helpers/utils");


exports.getShukranPointBalance = async function ({ req, res, pool }) {
    try{
        //API-3760
        const redisKey = getAppConfigKey('globalRedisKey') || '';
        // logger.info(`getShukranPointBalance: Redis key retrieved - ${redisKey}`);
        let accessToken = '';
        if(redisKey){
            const redisToken = await client.get(redisKey);
            // logger.info(`getShukranPointBalance: Redis token retrieved from epsilon bucket`);
            const token = JSON.parse(redisToken);
            accessToken = token?.access_token || '';
        }
        //API-3760 END

        const {
            profileId = ''
        } = req?.body || {};

        let resp = {};
        if(accessToken){
            //Getting User Points Balance
            const url = `${process.env.PROXY_HOST}/api/v1/profiles/${profileId}/points/balance`;
            const programCode = 'SHUKRAN';
            const balance = await axios.get(url, {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'OAuth '+accessToken,
                    'Program-Code': programCode,
                    'Accept-Language': 'en-US'
                },
            }).catch((err) => {});

            resp = balance?.data || {}
        }

        return res.status(200).json({
            status: true,
            statusCode: "200",
            response: resp
        });
    } catch (e) {
      return res.status(500).json({
        status: false,
        statusCode: "500",
        statusMsg: e.message,
      });
    }
};