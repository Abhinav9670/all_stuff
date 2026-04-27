const { ORDER_INVOICE_ENDPOINT } = require('../constants/javaEndpoints');
const catchAsync = require('../utils/catchAsync');
const axios = require('axios');
const fs = require('fs');
const {
  prepareInvoice,
  findOrders,
  storeInvoice,
  findAllInvoice,
  getInvoiceTemplateSource
} = require('../helpers/invoice');
const { getStoreConfigs } = require('../utils/config');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];
const AdmZip = require('adm-zip');
const { getQrCodeStr } = require('../utils');
const { setQrCode } = require('../services/creditmemo.service');
const { invoiceOptions, getCompanyEmail } = require('../utils/pdf');
const { uploadFile } = require('../config/googleStorage');
const moment = require('moment');
const _ = require('lodash');
const dateFormat = 'Y-MM-DDTHH:MM';
const handlebars = require('handlebars');
const playwright = require('playwright');

const generateInvoice = catchAsync(async (req, res) => {
  buildInvoiceData(req);
  res.status(200).send({
    status: true
  });
});

const buildInvoiceData = async req => {
  const { fromDate: from, toDate: to, country } = req.body;
  const orders = await findOrders(req.body);
  await fs.mkdirSync('./downloads', { recursive: true }); // recursive: true creates parent directories as needed
  const zip = new AdmZip();
  const browser = await playwright.chromium.launch({
    executablePath: '/usr/bin/chromium-browser',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu','--disable-dev-shm-usage','--disable-background-timer-throttling','--disable-features=AudioServiceOutOfProcess,IsolateOrigins,site-per-process','--disable-infobars','--disable-print-preview','--hide-scrollbars','--mute-audio'],
    // executablePath:"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",   //  for local windows chrome path 
      //   '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', // for local chrome path
    headless: true,
});
  const page = await browser.newPage(); 
  for (const orderData of orders) {
    await prepareInvoiceZip({orderData, req, zip, page});
  }
  await browser.close();
  const fromDate = moment(from).format(dateFormat);
  const toDate = moment(to).format(dateFormat);
  const fileName = `${fromDate}_${toDate}`;
  const zipFileName = `./downloads/${fileName}.zip`;
  zip.writeZip(zipFileName);
  const file = {
    tempFilePath: zipFileName,
    name: fileName
  };
  const { success, fileName: bucketPath } = await uploadFile(file, 'invoice');
  unlink(zipFileName);
  if (success) {
    await storeInvoice({
      country,
      from_date: from,
      to_date: to,
      bucket_path: bucketPath
    });
  }
};

const prepareInvoiceZip = async ({orderData, req, zip, page}) => {
  try {
    const { orderId, customerEmail } = orderData;
    const filePath = `./downloads/invoice_${orderId}.pdf`;
    const { invoiceData, qrCodeStr, companyEmail, zatcaQrCode } = await fetchInvoiceData(
      orderId,
      customerEmail,
      req
    );
    const templateSource = getInvoiceTemplateSource({
      zatcaStatus: invoiceData?.zatcaStatus
    });
    
    setQrCode({
      data: invoiceData,
      qrCodeStr,
      storeId: invoiceData.storeId,
      zatcaQrCodeStr: zatcaQrCode
    });

    const template = handlebars.compile(templateSource);
    const html = template({ data: invoiceData });

    await generatePdfFromHtml({html, filePath, page});

    const { fromDate: from, toDate: to } = req.body;
    const fromDate = moment(from).format(dateFormat);
    const toDate = moment(to).format(dateFormat);
    const storesData = global.config?.environments?.[0]?.stores;
    const storeName = storesData
      ?.filter(st => st.storeId === invoiceData.storeId)
      .map(st => _.toUpper(st.websiteCode));

    await zip.addFile(
      `invoice_${fromDate}_${toDate}/${storeName[0]}/${invoiceData.orderIncrementId}.pdf`,
      fs.readFileSync(filePath)
    );

    unlink(filePath);
  } catch (error) {
    console.log('Error in generating invoice. ', error);
  }
};

const fetchInvoiceData = async (orderId, customerEmail, req) => {
  let invoiceData;
  let qrCodeStr;
  let zatcaQrCode;
  let companyEmail = 'hello.ksa@stylishop.com';
  try {
    let result = await axios.post(
      ORDER_INVOICE_ENDPOINT,
      { orderId: orderId, customerEmail },
      {
        headers: {
          Authorization: req.headers?.authorization || '',
          'authorization-token': internalAuthToken
        }
      }
    );
    let { data } = result;

    if (!data.status) {
      console.log('going to archive db to fetch invoice for order: ' + orderId);
      result = await axios.post(
        ORDER_INVOICE_ENDPOINT,
        { orderId: orderId, customerEmail, useArchive: true },
        {
          headers: {
            Authorization: req.headers?.authorization || '',
            'authorization-token': internalAuthToken
          }
        }
      );
      data = result.data;
    }

    const { response: apiResponse } = data;
    const { invoicedAmount, taxAmount } = apiResponse?.totals || {};
    const { storeId, updatedAt: date, warehouseId } = apiResponse || {};
    companyEmail = getCompanyEmail({ warehouseId, storeId });

    let showTax = true;
    const configValue = getStoreConfigs({ key: 'taxPercentage', storeId });
    if (configValue.length) {
      const taxPercentage = configValue[0].taxPercentage;
      if (!taxPercentage || taxPercentage === 0) showTax = false;
    }

    invoiceData = await prepareInvoice({ apiResponse, showTax });
    qrCodeStr = getQrCodeStr({
      storeId,
      date,
      invoicedAmount,
      taxAmount
    });
    zatcaQrCode = invoiceData.zatcaQrCode;
  } catch (e) {
    console.log(e);
  }
  return { invoiceData, qrCodeStr, companyEmail, zatcaQrCode };
};

const findAllGeneratedInvoice = catchAsync(async (req, res) => {
  const invoices = await findAllInvoice();
  invoices?.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  res.status(200).send({
    status: true,
    response: invoices
  });
});

const unlink = file => {
  fs.unlink(file, err => {
    if (err) console.error('Error in unlink downloaded files', err);
  });
};

const generatePdfFromHtml = async ({html, filePath, page}) => {
  try {
    
    await page.setContent(html);
    await page.pdf({
      path: filePath,
      format: 'A4'
    });
    console.log(`PDF generated successfully: ${filePath}`);
  } catch (e) {
    global.logError(`PDF generated :: ${e.message}`);
  }
};

module.exports = {
  generateInvoice,
  findAllGeneratedInvoice
};