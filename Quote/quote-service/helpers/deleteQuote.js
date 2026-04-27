const { logError } = require('../helpers/utils')
const { initcluster } = require('../config/couchbase.js')
// const { logger } = require('./utils')

exports.deleteQuote = async function ({ dataFromKafka }) {
  // logger.info(`Kafka deleteQuote: ${JSON.stringify(dataFromKafka)}`)
  const { customerId } = dataFromKafka
  const apm = global?.apm;
  let span;
  try {
    const cluster = await initcluster();
    const query = `DELETE FROM ${process.env.COUCHBASE_CLUSTER_BUCKET} where customerId="${customerId}"`
    span = apm?.startSpan('CB: Query deleteQuote', 'db', 'couchbase', 'query');
    if (span) {
      span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    await cluster.query(query)
    return true;
  } catch (e) {
    logError(e, `${dataFromKafka} - could not execute delete query`)
    return false;
  } finally {
    if (span) span.end();
  }
}
