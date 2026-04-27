
require('dotenv-expand')(require('dotenv').config({
    path: require('find-config')(`.env.${process.env.NODE_ENV || 'development.local'}`)
}));

const mysql = require('../config/mySqlConnection');
const batchSize = Number(process.env.IMPORT_BATCH_SIZE || 100);

const getQuoteCount = async () => {
    const quoteCountQuery = "select count(entity_id) as quoteCount from quote where is_active=1";
    const countResponse = await mysql.query(quoteCountQuery);
    let [data] = countResponse;
    data = JSON.parse(JSON.stringify(data));
    const quoteCount = data[0].quoteCount;
    const maxPageNum = Math.floor(quoteCount / batchSize);

    // console.log('total quote count', quoteCount)
    // console.log('total number of pages', maxPageNum)
    // console.log('batch size', batchSize)

}

getQuoteCount();