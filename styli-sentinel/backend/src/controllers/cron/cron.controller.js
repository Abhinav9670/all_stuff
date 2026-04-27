const { generateEpsilonToken } = require('../../services/cron.service');
const { client } = require('../../config/redis-config');

exports.generateEpsilonToken = async ({ req, res }) => {
  try {
    const response = await generateEpsilonToken();
    console.log('Epsilon auth response', JSON.stringify(response));
    if (response?.status == 201) {
      console.log('Successfully invoked generate epsilon token', response.status);
      const cacheName = 'epsilon-bucket';
      const key = 'epsilon-token';
      const redisKey = `${cacheName}:${key}`;
      // Save into Redis
      try {
        //if client is not open connect again
        if (!client.isOpen) {
          await client.connect();
        }
        const redisSavedtoDB = await client.set(redisKey, JSON.stringify(response.data));
        if (!redisSavedtoDB) {
          console.log('Data not updated in Redis for client and key', redisKey);
        } else {
          console.log('Data saved in Redis for client and key ', redisKey);
        }

        try {
          const value = await client.get(redisKey); // Get the value associated with the key
          if (value) {
            console.log(`Value for ${redisKey}:`, value);
          } else {
            console.log(`Key ${redisKey} does not exist`);
          }
        } catch (error) {
          console.error('Error retrieving value from Redis:', error);
        }
      } catch (e) {
        console.log(`${redisKey} - Error upserting data in Redis`, e);
      }

      return res.status(200).send({ status: 'Success', data: response.data });
    } else {
      console.log('Error in invoking generate epsilon token', JSON.stringify(response));
      return res.status(400).send({
        status: 'failed',
        message: 'Error in invoking generate epsilon token'
      });
    }
  } catch (error) {
    console.error('Error in generateEpsilonToken function:', JSON.stringify(error));
    return res.status(500).send({
      status: 'failed',
      message: 'Internal server error',
      error: error.message
    });
  }
};
