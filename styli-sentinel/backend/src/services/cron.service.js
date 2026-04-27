require('dotenv').config();
const axios = require('axios');
const qs = require('qs');

const data = qs.stringify({
  username: process.env.EPSILON_USERNAME,
  password: process.env.EPSILON_PASSWORD,
  grant_type: process.env.EPSILON_GRANT_TYPE,
  response_type: process.env.EPSILON_RESPONSE_TYPE
});

const EPSILON_AUTH_TOKEN_SUB_URL = '/api/v1/authorization/tokens';

exports.generateEpsilonToken = async () => {
  try {
    // Log the environment variables and data
    console.log('EPSILON_BASE_URL:', process.env.EPSILON_BASE_URL);
    console.log('EPSILON_AUTH_TOKEN_SUB_URL:', EPSILON_AUTH_TOKEN_SUB_URL);
    console.log('EPSILON_AUTH_KEY:', process.env.EPSILON_AUTH_KEY);
    console.log('Data being sent:', data);
    const finalToken = `Basic ${process.env.EPSILON_AUTH_KEY}`;
    console.log('Epsilon finalToken:', finalToken);
    const response = await axios.post(process.env.EPSILON_BASE_URL + EPSILON_AUTH_TOKEN_SUB_URL, data, {
      headers: {
        Authorization: finalToken,
        'Accept-Language': 'en-US',
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      timeout: 10000 // Set timeout to 10 seconds
    });

    // Log the response data
    console.log('Epsilon Response:', response);
    console.log('Epsilon Response data:', JSON.stringify(response.data));
    console.log('Epsilon Response status:', response.status);

    return {
      data: response.data,
      status: response.status
    };
  } catch (error) {
    // Log the error response
    console.log('Epsilon Error :', error);
    console.log('Epsilon Error data:', error?.response?.data?.error);
    console.log('Epsilon Error message:', error?.response?.data?.error?.message);
    console.log('Epsilon Error status:', error?.response?.status);
    console.log('Epsilon Error statusText:', error?.response?.statusText);

    return {
      data: error?.response?.data?.error,
      message: error?.response?.data?.error?.message,
      status: error?.response?.status,
      statusText: error?.response?.statusText
    };
  }
};
