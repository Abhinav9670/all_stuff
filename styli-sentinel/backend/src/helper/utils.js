const logger = require("./logger");

const escapeRegex = (str) => str.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&')
function getApigeeProxyPath() {//used to get apigee proxy path
    let routePrefix = '', NODE_ENV = process.env.NODE_ENV_ALT || process.env.NODE_ENV;
    logger.info('sentinel NODE_ENV', NODE_ENV)
    if (NODE_ENV === 'development' || NODE_ENV === 'dev' || NODE_ENV === 'local') {
        routePrefix = '/sentinel-dev';
    } else {
        routePrefix = '/sentinel-prod';
    }
    logger.info('sentinel routePrefix', routePrefix)
    return routePrefix;
}


module.exports = {
    escapeRegex,
    getApigeeProxyPath
}