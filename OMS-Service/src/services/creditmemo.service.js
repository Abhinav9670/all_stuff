const { getCreditMemo, prepareCreditMemo } = require('../helpers/creditMemo');
const moment = require('moment');
const { getStoreConfigs, getFeatureEnabled } = require('../utils/config');
const { getQrCodeStr, getNumericValue } = require('../utils');
const fs = require('fs');
const QRCode = require('qrcode');
const { sendSgEmail } = require('./email.service');
const { STORE_LANG_MAP } = require('../constants');
const handlebars = require('handlebars');
const { getStoreWiseHeadings } = require('../helpers/invoiceHeadings');
const playwright = require('playwright');
const { prepareHtml } = require('../helpers/email');
const { getRmaShipmentDetail } = require('../helpers/rma');
const { getStoreLink, getWebsiteLink } = require('../helpers/store');

const processCreditmemoEmail = async ({
  document,
  filePath,
  entityId,
  html = 'Content Not Available!',
  orderIncrementId,
  template,
  increment_id,
  incrementIdData,
  awbNumber,
  pickupFailedDate,
}) => {
  if (!global.baseConfig?.emailConfig?.sendCreditmemoEmail || !increment_id) return;
  const storeId = document?.context?.data?.store_id || 1;
  // let content = '<p>';
  let subject = template =="pickup_failed"? `Return Pickup Attempt Unsuccessful - AWB ${awbNumber}` :`Refund Update - Order #${orderIncrementId}`;
  if (STORE_LANG_MAP[Number(storeId)] == 'ar') {
    // content += '<p dir="rtl">';
    subject = template=="pickup_failed" ? `تم فشل محاولة استلام طلب الإرجاع - بوليصة رقم ${awbNumber}`: `تحديث لطلب الاسترجاع - رقم الطلب ${orderIncrementId}`;
  }
  // content += html;
  // content += '</p>';
  let content2 = "";
  const rmaDetailResp = await getRmaShipmentDetail({
    rmaIncrementId: increment_id
  });
  const returnData = rmaDetailResp?.data || [];
  content2 = prepareHtml({
    template:['refund_completed_cod','refund_completed_online','pickup_failed'].includes(template) ? `${template}_${STORE_LANG_MAP[Number(storeId)]}` :`default_${STORE_LANG_MAP[Number(storeId)]}`,
    data: {
      firstname: returnData?.address?.firstname,
      awbNumber,
      pickupFailedDate,
      returnData,
      storeLink:getStoreLink(storeId),
      websiteLink: getWebsiteLink(storeId),
      incrementId:orderIncrementId,
      sms:['refund_completed_cod','refund_completed_online','pickup_failed'].includes(template) ? undefined : html
    }
  })
  const companyEmail = document.context?.data?.companyEmail;
  /* const options =
    process.env?.REGION?.toUpperCase() === 'IN'
      ? creditMemoOptions({
          companyEmail,
          format: process.env?.REGION?.toUpperCase()
        })
      : creditMemoOptions({ companyEmail }); */
  const toEmail = document?.context?.data?.customerEmail;
  const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
  if (!toEmail || !fromEmail || !fromName) {
    global.logError('Email fields not found for creditmemo');
  } else {
    try {
      const browser = await playwright.chromium.launch({
        args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu','--disable-dev-shm-usage','--disable-background-timer-throttling','--disable-features=AudioServiceOutOfProcess,IsolateOrigins,site-per-process','--disable-infobars','--disable-print-preview','--hide-scrollbars','--mute-audio'],
        executablePath: '/usr/bin/chromium-browser',
        // executablePath:"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",   //  for local windows chrome path 
          //   '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', // for local chrome path
        headless: true,
      });  
      const page = await browser.newPage(); 
      await page.setContent(document.html,{ waitUntil: 'domcontentloaded' });
      const pdfBuffer = await page.pdf({
        format: 'A4'
      });
      await browser.close();
          sendSgEmail({
        to: toEmail,
        from: { email: fromEmail, name: fromName },
        subject,
        html: content2,
        attachments: [
          {
            content: pdfBuffer.toString('base64'),
            filename: `creditmemo_${entityId}.pdf`,
            type: 'application/pdf',
            disposition: 'attachment',
            content_id: 'mypdf'
          }
        ]
      });
    } catch (error) {
      global.logError(error, "ERROR OCCURED FOR EMAIL REFUBD",JSON.stringify(document));
    }
  }
};

const getTaxPercentage = (subtotal, vatAmount, discountAmount) => {
  return getNumericValue(
    (Number(vatAmount) * 100) /
      (Number(subtotal) - Number(vatAmount) - Number(discountAmount))
  );
};

const getExcludingVatData = (data, response) => {
  const { tax_amount, subtotal_incl_tax, discount_amount } = response;
  let grandTotalExcludingVat = null;
  data.taxPercent = '';

  if (data.isUaeOrKsa) {
    const taxPercent = getTaxPercentage(
      subtotal_incl_tax,
      tax_amount,
      discount_amount
    );

    grandTotalExcludingVat =
      (Number(subtotal_incl_tax) - Number(discount_amount)) *
      (100 / (100 + Number(taxPercent)));

    const taxValue = (Number(taxPercent) * grandTotalExcludingVat) / 100;
    data.tax_amount = getNumericValue(taxValue);
    data.grandTotalExcludingVat = getNumericValue(grandTotalExcludingVat);
    data.grandTotalIncludingVat = getNumericValue(
      grandTotalExcludingVat + taxValue
    );
    if (Number(taxPercent) > 0) data.taxPercent = `${Number(taxPercent)}%`;
  }
};
const getStoreSpecificHearder = (data, response) => {
  const { store_id: storeId } = response;
  response.isUaeOrKsa = [1, 3, 7, 11].includes(storeId);
  response.isUae = [7, 11].includes(storeId);
  response.isKsa = [1, 3].includes(storeId);
  response.isExcludeKsa = [7, 11, 13, 12, 15, 17, 19, 21, 51, 23, 25].includes(storeId);
  data.isKsa
    ? (data.creditheadingar = 'الإشعار الدائن الضريبي')
    : (data.creditheadingar = 'الإشعار الدائن');
  data.isKsa
    ? (data.creditheadingen = 'Tax Credit Note')
    : (data.creditheadingen = 'Credit Note');
};

const getCreditMemoDocument = async ({ entityId, filePath, creditMemo, status, paymentData, useArchive= false }) => {
  let data;
  let qrCodeStr;
  try {
    const { error, response } = await getCreditMemo({
      entity_id: entityId,
      includeOrder: true,
      includeSubOrder: true,
      includeRmaTracking: true,
      includeInvoice: true,
      creditMemo: creditMemo,
      useArchive 
    });

    if (error) return { error };

    const {
      store_id: storeId,
      created_at,
      grand_total,
      amstorecredit_amount,
      tax_amount: taxAmount
    } = response || {};
    const invoicedAmount =
      Number(grand_total) + Number(amstorecredit_amount || 0);
    const date = created_at
      ? moment(created_at).format('YYYY-MM-DD HH:mm:ss')
      : 'N/A';

    let showTax = true;
    const configValue = getStoreConfigs({ key: 'taxPercentage', storeId });
    if (configValue.length) {
      const taxPercentage = configValue[0].taxPercentage;
      if (!taxPercentage || taxPercentage === 0) showTax = false;
      // console.log('taxPercentageValue', taxPercentage, showTax);
    }
    const rateConfigValue = getStoreConfigs({
      key: 'currencyConversionRate',
      storeId
    });
    let factor = 1;
    if (rateConfigValue.length) {
      factor = rateConfigValue[0].currencyConversionRate;
    }
    data = await prepareCreditMemo({ response, showTax, factor, status, paymentData, useArchive });
    getStoreSpecificHearder(data, response);
    getExcludingVatData(data, response);
    data.currencyText = {
      en: `(Value in ${response.order_currency_code})`,
      ar: `(${response.order_currency_code} القيمة بال)`
    };
    if (data.isUae) data.currencyText.ar = '(القيمة بالدرهم الإماراتي)';
    if (data.isKsa) data.currencyText.ar = '(القيمة بالريال السعودي)';

    data.headers = getStoreWiseHeadings(data);

    qrCodeStr = getQrCodeStr({
      storeId,
      date,
      invoicedAmount,
      taxAmount
    });
  } catch (e) {
    console.log(e);
  }

  const templateSource = getCreditMemoTemplateSource({
    zatcaStatus: data?.zatca_status
  });

  // if (qrCodeStr) setQrCodeOld(data, qrCodeStr);
  setQrCode({data, qrCodeStr, storeId: data.store_id, zatcaQrCodeStr: data.zatca_qr_code });

  const template = handlebars.compile(templateSource);
  const html = template({ data: data });

  return {
    html: html,
    context: { data: data }
  };
};

const setQrCode = ({data, qrCodeStr, storeId, zatcaQrCodeStr = null}) => {
  if (getFeatureEnabled({ key: 'zatca', storeId: storeId })) {
    if (zatcaQrCodeStr){
      setZatcaQrCode(data, zatcaQrCodeStr);
      return;
    }
  }
  if (qrCodeStr) {
    setQrCodeOld(data, qrCodeStr);
    return;
  }
}

const setQrCodeOld = (object, qrCodeStr) => {
  QRCode.toString(
    qrCodeStr,
    { type: 'svg', scale: 4, width: 100 },
    function (err, output) {
      if (err) global.logError(err);
      object.qrCode = output;
    }
  );
};

const setZatcaQrCode = (object, zatcaQrCodeStr) => {
  object.qrCode = `<image width="100" height="100" src="data:image/png;base64,${zatcaQrCodeStr}" />`;
};

const getCreditMemoTemplateSource = ({ zatcaStatus }) => {
  let templateSource = '';
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    templateSource = fs.readFileSync(
      './src/templates/in/creditmemo.html',
      'utf8'
    );
  } else {
    templateSource = zatcaStatus
      ? fs.readFileSync('./src/templates/creditMemoZatca.html', 'utf8')
      : fs.readFileSync('./src/templates/creditmemo.html', 'utf8');
  }
  return templateSource;
};

module.exports = {
  getCreditMemoDocument,
  processCreditmemoEmail,
  setQrCode
};