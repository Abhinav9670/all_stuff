const mobileValidate = index => async (req, res, next) => {

    console.log(req?.ip, ":::::::::IP ADDRESS FOR REQUEST:::::::::::");
    console.log(`DATA FOR HEADER ${JSON.stringify(req.headers)} IN MIDDLEWARE FOR GENERATED PDF API JSON DATA ALL`);

    const userAgent = req.headers['user-agent'] || req.headers['User-Agent'];

    const hostHeader = req.headers['host'];
    const originHeader = req.headers['origin'];
    const refererHeader = req.headers['referer'];
    const fullUrl = `${req.protocol}://${req.get('host')}${req.originalUrl}`;

    console.log('[generatePDF]::: Host Header:', hostHeader);
    console.log('[generatePDF]::: Origin Header:', originHeader);
    console.log('[generatePDF]::: Referer Header:', refererHeader);
    console.log('[generatePDF]::: Full Requested URL:', fullUrl);

    const isDartClient = userAgent?.substring(0, 4).toLowerCase() === 'dart';

    const whiteListedIp = global?.baseConfig?.generatePDFWhiteListIPs || [];

    const clientIp = getClientIp(req);

    console.log('[generatePDF]::: Client IP:', clientIp);

    const isIncreff = whiteListedIp.includes(clientIp);

    if(isDartClient ||isIncreff){
        next();
    }
    else {
        return res.status(401).json({
            status: true,
            statusCode: '401',
            statusMsg: 'You are not authenticated for this request'
          });
    }

}

 const getClientIp = (req) => {
        return (
            req.headers['cf-connecting-ip'] ||
            req.headers['x-original-forwarded-for'] ||
            (req.headers['x-forwarded-for'] ? req.headers['x-forwarded-for'].split(',')[0].trim() : null) ||
            req.ip
        );
    };
 
module.exports = mobileValidate;