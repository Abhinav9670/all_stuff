const { ORDER_INVOICE_ENDPOINT } = require('../constants/javaEndpoints');
const catchAsync = require('../utils/catchAsync');
const axios = require('axios');
const { getStoreConfigs } = require('../utils/config');
const httpStatus = require('http-status');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];
const { getQrCodeStr } = require('../utils');
const CACHE_CONTROL_LITERAL = 'Cache-Control';
const {
  getCreditMemoDocument,
  processCreditmemoEmail,
  setQrCode
} = require('../services/creditmemo.service');
const { getCompanyEmail } = require('../utils/pdf');
const { Creditmemo, Order, SplitSalesOrder } = require('../models/seqModels/index');
const {
  prepareInvoice,
  getInvoiceTemplateSource,
  getSecondReturnTemplateSource,
  getCodRtoCreditMemoTemplateSource,
  dedupeInvoiceProductsBySku
} = require('../helpers/invoice');
const { 
Creditmemo : CreditmemoArchive, 
Order:OrderArchive
} = require('../models/seqModels/archiveIndex');
const { getKSATime } = require('../helpers/moment');
const handlebars = require('handlebars');
const { getRmaData } = require('../helpers/rma');
const playwright = require('playwright');
const {
  getOrderData
} = require('../helpers/order');
const { archiveSquelize } = require('../models/seqModels/archiveIndex');

const launchBrowser = async () => {
  try {
    return await playwright.chromium.launch({
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-gpu',
        '--disable-dev-shm-usage',
        '--disable-background-timer-throttling',
        '--disable-features=AudioServiceOutOfProcess,IsolateOrigins,site-per-process',
        '--disable-infobars',
        '--disable-print-preview',
        '--hide-scrollbars',
        '--mute-audio'
      ],
      executablePath: '/usr/bin/chromium-browser',
      // // executablePath:"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",   //  for local windows chrome path 
      //   //   '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', // for local chrome path
      headless: true // Use headless to run without a GUI
    });
  } catch (error) {
    global.logError(error);
    console.log('launchBrowser error', error);
  }
};

const generatePDF = catchAsync(async (req, res) => {
  res.set(CACHE_CONTROL_LITERAL, 'no-store');
  console.log(`HEADER DATA ${JSON.stringify(req.headers)} IN OMS-SERVICE FOR GENERATE PDF API`);

let orderId, splitOrderId, customerEmail;

if (req.params.orderId) {
  const { orderId: encodedId } = req.params;
  const decodedStr = Buffer.from(encodedId, 'base64').toString() || '';
  const splittedDecodedStr = decodedStr.split('#');

  if (splittedDecodedStr.length === 3) {
    [orderId, splitOrderId, customerEmail] = splittedDecodedStr;
  } else if (splittedDecodedStr.length === 2) {
    if(splittedDecodedStr[1].includes('@')) {
      [orderId, customerEmail] = splittedDecodedStr;
    } else {
      [orderId, splitOrderId] = splittedDecodedStr;
    }
  } else if (splittedDecodedStr.length === 1) {
    [orderId] = splittedDecodedStr;
  }
  else {
    throw new Error('Invalid request');
  }
  if(!splitOrderId){
    splitOrderId = orderId;
  }
} else if (req.body.customerEmail) {
  ({ orderId, splitOrderId, customerEmail } = req.body);
}

if (req.body.splitOrderId) {
  splitOrderId = req.body.splitOrderId;
}
  const filePath = `invoice_${splitOrderId || orderId}.pdf`;
  await generateInvoicePdf(res, filePath, orderId, customerEmail, req, splitOrderId);
});

const generateInvoicePdf = async (
  res,
  filePath,
  orderId,
  customerEmail,
  req,
  splitOrderId,
  qrCode,
  isCreditMemo,
  creditMemoCreationDate
) => {
  const {
    invoiceData,
    qrCodeStr,
    companyEmail,
    zatcaQrCode
  } = await fetchInvoiceData(orderId, customerEmail, req, splitOrderId);

  if (!invoiceData) {
    return res.status(200).json({});
  }
  if (isCreditMemo && invoiceData.products?.length) {
    invoiceData.products = dedupeInvoiceProductsBySku(invoiceData.products);
  }
  if (
    invoiceData?.totals?.grandTotal &&
    invoiceData?.totals?.giftVoucherAmount
  ) {
    invoiceData.totals.discountAmount =
      Number(invoiceData.totals.discountAmount) -
      parseFloat(invoiceData?.totals?.giftVoucherAmount.match(/\d+\.\d+/)[0]);
    invoiceData.totals.discountAmount =
      Number(invoiceData.totals.discountAmount) > 0
        ? Number(invoiceData.totals.discountAmount).toFixed(2)
        : 0;
  }
  if(invoiceData?.totals?.grandTotal && invoiceData?.totals?.totalShukranBurnedPoints){
    invoiceData.totals.grandTotal = (parseFloat(invoiceData.totals.grandTotal)-parseFloat(invoiceData.totals.totalShukranBurnedValueInCurrency || 0)).toFixed(2)
  }  
  let templateSource = getInvoiceTemplateSource({
    zatcaStatus: invoiceData?.zatcaStatus
  });

  setQrCode({
    data: invoiceData,
    qrCodeStr,
    storeId: invoiceData.storeId,
    zatcaQrCodeStr: qrCode ? qrCode : zatcaQrCode
  });

  if (isCreditMemo) {
    templateSource = getCodRtoCreditMemoTemplateSource();
    invoiceData.creditMemoDate = creditMemoCreationDate;
    invoiceData.headers.invoiceHeading.en =
      'Tax Credit Note';
    invoiceData.headers.invoiceHeading.ar = 'الإشعار الدائن الضريبي';
    if (invoiceData.totals.currency !== 'SAR') {
      invoiceData.headers.invoiceHeading.en = 'Credit Note';
      invoiceData.headers.invoiceHeading.ar = 'الإشعار الدائن';
    }
  }

  try {
    const template = handlebars.compile(templateSource);
    const html = template({ data: invoiceData });
    const browser = await launchBrowser();
    const page = await browser.newPage();
    await page.setContent(html, { waitUntil: 'domcontentloaded' });

    const pdfBuffer = await page.pdf({
      format: 'A4'
    });
    await browser.close();

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename=${filePath}`);

    res.send(pdfBuffer);
  } catch (error) {
    global.logError(error);
  }
};

const emailCreditMemoPDF = catchAsync(async (req, res) => {
  res.set(CACHE_CONTROL_LITERAL, 'no-store');
  res.status(200).json({ message: 'Request received!' });

  const { entityId: encodedId } = req.params;
  const entityId = Buffer.from(encodedId, 'base64').toString();
  if (!entityId) return res.status(500).json({ error: 'ID not received!' });

  try {
    const filePath = `creditmemo_${entityId}.pdf`;
    const document = await getCreditMemoDocument({
      entityId,
      filePath
    });

    processCreditmemoEmail({ document, filePath, entityId });
  } catch (e) {
    console.log('Error sending creditmemo email.');
    global.logError(e);
  }
});

const generateCreditMemoPDF = catchAsync(async (req, res) => {
  res.set(CACHE_CONTROL_LITERAL, 'no-store');

  let entityId;
  let useArchive = req.body.useArchive;
  if (req.params && req.params.entityId) {
    entityId = Buffer.from(req.params.entityId, 'base64').toString();
  }
  else if (req.body && req.body.entityId) {
    entityId = req.body.entityId;
  }
  if (!entityId) res.status(500).json({ error: 'ID not received!' });


  const creditMemo = useArchive ?  await CreditmemoArchive.findOne({
    where: { entity_id: entityId },
    raw: true
  }) : await Creditmemo.findOne({
    where: { entity_id: entityId },
    raw: true
  })

  const filePath = `creditmemo_${entityId}.pdf`;
  if (creditMemo) {
    if (creditMemo.memo_type && creditMemo.memo_type === 'codRto') {
      const orderData = useArchive ? await OrderArchive.findOne({
        where: { entity_id: creditMemo.order_id }
      }) : await Order.findOne({
        where: { entity_id: creditMemo.order_id }
      });

      // For split orders the Java invoice endpoint stores products under orders[n].products,
      // not at apiResponse.products. Resolve the split order id so prepareInvoice picks the
      // correct products branch instead of falling back to the empty top-level array.
      let codRtoSplitOrderId = null;
      if (!useArchive) {
        const splitOrder = await SplitSalesOrder.findOne({
          where: { order_id: creditMemo.order_id },
          attributes: ['entity_id'],
          raw: true
        });
        if (splitOrder) codRtoSplitOrderId = splitOrder.entity_id;
      }
      await generateInvoicePdf(
        res,
        filePath,
        creditMemo.order_id,
        orderData.customer_email,
        req,
        codRtoSplitOrderId,
        creditMemo.zatca_qr_code,
        true,
        getKSATime(creditMemo.created_at)
      );
    } else {
      const order = await getOrderData({orderId: creditMemo.order_id, includeSubOrder: true,useArchive});
      const paymentData = order?.["OrderPayments.method"] || '';
      const document = await getCreditMemoDocument({
        entityId,
        filePath,
        creditMemo,
        status: order?.status,
        paymentData,
        useArchive,
        splitOrderId: null
      });
      if (document.error)
        return res
          .status(httpStatus.INTERNAL_SERVER_ERROR)
          .json({ error: document.error });
      try {
        const browser = await launchBrowser();
        const page = await browser.newPage();

        await page.setContent(document.html, { waitUntil: 'domcontentloaded' });
        const pdfBuffer = await page.pdf({
          format: 'A4'
        });
        await browser.close();

        res.setHeader('Content-Type', 'application/pdf');
        res.setHeader(
          'Content-Disposition',
          `attachment; filename=${filePath}`
        );
        res.send(pdfBuffer);
      } catch (error) {
        global.logError(error);
      }
    }
  }
  else {
    return res.status(500).json({ error: `CREDIT MEMO NOT FOUND, VALUE = ${creditMemo}` });
  }
});

const fetchInvoiceData = async (orderId, customerEmail, req, splitOrderId) => {
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

    // Try archive db if invoice not found in live db
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
    const { orders } = apiResponse;

    let totals; 
    if(splitOrderId){
      const splitOrder = orders.find(order => order.orderId === +splitOrderId);
      totals = splitOrder.totals;
    } else {
      totals = apiResponse.totals;
    }
    const { invoicedAmount, taxAmount } = totals || {};
    const { storeId, updatedAt: date, warehouseId } = apiResponse || {};
    companyEmail = getCompanyEmail({ warehouseId, storeId });
    let taxPercentage;
    let showTax = true;
    const configValue = getStoreConfigs({ key: 'taxPercentage', storeId });
    if (configValue.length) {
      taxPercentage = configValue[0].taxPercentage;
      if (!taxPercentage || taxPercentage === 0) showTax = false;
      // console.log('taxPercentageValue', taxPercentage, showTax);
    }

    invoiceData = await prepareInvoice({
      apiResponse,
      showTax,
      configTax: taxPercentage,
      splitOrderId
    });
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

const generateSecondReturnInvoice = async (req, res) => {
  try {
    res.set(CACHE_CONTROL_LITERAL, 'no-store');
    const { rmaIncId } = req.params;
    const rmaData = await getRmaData(rmaIncId);

    if (rmaData && rmaData.zatca_details) {
      const filePath = `Second_Return_${rmaIncId}.pdf`;

      const templateSource = getSecondReturnTemplateSource();
      const zatcaDetails = JSON.parse(rmaData.zatca_details);
      const qrCode = `<image width="100" height="100" src="data:image/png;base64,${rmaData.zatca_qr_code}" />`;
      const zatcaData = {
        headers: {
          invoiceHeading: {
            en: 'Simplified Tax Invoice',
            ar: 'فاتورة ضريبية مبسطة'
          }
        },
        qrCode: qrCode,
        incrementId: zatcaDetails['EInvoice']['ID']['en'],
        createdAt: zatcaDetails['EInvoice']['IssueDate'],
        issueTime: zatcaDetails['EInvoice']['IssueTime'],
        currency: zatcaDetails['EInvoice']['DocumentCurrencyCode'],
        seller: {
          name:
            zatcaDetails['EInvoice']['AccountingSupplierParty']['Party'][
              'PartyLegalEntity'
            ]['RegistrationName']['en'],
          streetAddress:
            zatcaDetails['EInvoice']['AccountingSupplierParty']['Party'][
              'PostalAddress'
            ]['StreetName']['en'],
          area:
            zatcaDetails['EInvoice']['AccountingSupplierParty']['Party'][
              'PostalAddress'
            ]['CityName']['en'],
          city:
            zatcaDetails['EInvoice']['AccountingSupplierParty']['Party'][
              'PostalAddress'
            ]['CitySubdivisionName']['en'],
          country:
            zatcaDetails['EInvoice']['AccountingSupplierParty']['Party'][
              'PostalAddress'
            ]['Country']['IdentificationCode']
        },
        vat:
          zatcaDetails['EInvoice']['AccountingSupplierParty']['Party'][
            'PartyTaxScheme'
          ]['CompanyID'],
        unitPrice: zatcaDetails['CustomFields']['Base Payable Amount'],
        totalPrice: zatcaDetails['CustomFields']['Payable Amount'],
        taxAmount:
          zatcaDetails['EInvoice']['InvoiceLine'][0]['TaxTotal']['TaxAmount'][
            'value'
          ],
        taxPercentage: zatcaDetails['EInvoice']['InvoiceLine'][0]['Item'][
          'ClassifiedTaxCategory'
        ]['Percent']
          ? zatcaDetails['EInvoice']['InvoiceLine'][0]['Item'][
              'ClassifiedTaxCategory'
            ]['Percent']
          : 0
      };

      const template = handlebars.compile(templateSource);
      const html = template({ data: zatcaData });

      const browser = await launchBrowser();
      const page = await browser.newPage();
      await page.setContent(html, { waitUntil: 'domcontentloaded' });
      const pdfBuffer = await page.pdf({
        format: 'A4'
      });
      await browser.close();

      res.setHeader('Content-Type', 'application/pdf');
      res.setHeader('Content-Disposition', `attachment; filename=${filePath}`);
      res.send(pdfBuffer);
    } else {
      throw new Error('No Data Available To Print');
    }
  } catch (err) {
    res.status(400).json({ error: e.message });
  }
};

module.exports = {
  generatePDF,
  generateCreditMemoPDF,
  emailCreditMemoPDF,
  generateSecondReturnInvoice
};