const fs = require('fs');
const httpStatus = require('http-status');
const XLSX = require('xlsx');
const { Op } = require('sequelize');
const catchAsync = require('../utils/catchAsync');
const { SellerConfig, SellerCommissionDetails } = require('../models/seqModels/index');

const parseExcelFile = filePath => {
  const workbook = XLSX.readFile(filePath);
  const sheetName = workbook.SheetNames[0];

  if (!sheetName) {
    return [];
  }

  return XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], {
    defval: null // keep empty cells as null for easier validation
  });
};

const normalizeValue = value => (typeof value === 'string' ? value.trim() : value);
const normalizeSellerId = value => (value === null || value === undefined ? '' : `${value}`.trim());

const upsertCommissionDetails = catchAsync(async (req, res) => {
  const uploadedFilePath = req.file?.path;

  try {
    if (!req.file) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        message: 'Excel file with commission details is required'
      });
    }

    const rows = parseExcelFile(uploadedFilePath);

    if (!rows.length) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        message: 'Uploaded Excel file is empty or unreadable'
      });
    }

    const successes = [];
    const errors = [];
    const knownColumns = new Set([
      'l4Category',
      'seller_name',
      'seller_id',
      'commission_value',
      'commission_details'
    ]);

    const sellerIdsInSheet = Array.from(
      new Set(
        rows
          .map(row => normalizeSellerId(row.sellerId ?? row.seller_id))
          .filter(Boolean)
      )
    );

    const existingSellers = sellerIdsInSheet.length
      ? await SellerConfig.findAll({ where: { SELLER_ID: { [Op.in]: sellerIdsInSheet } } })
      : [];
    const sellerIdSet = new Set(existingSellers.map(s => s.SELLER_ID));

    const existingCommissionRows = sellerIdsInSheet.length
      ? await SellerCommissionDetails.findAll({ where: { seller_id: { [Op.in]: sellerIdsInSheet } } })
      : [];
    const toKey = (sellerId, l4Category) => `${sellerId}||${l4Category || ''}`;
    const commissionByCompositeKey = new Map(existingCommissionRows.map(row => [toKey(row.seller_id, row.l4_category), row]));
    const commissionUpsertMap = new Map(); // compositeKey -> payload

    for (const [index, row] of rows.entries()) {
      const rowNumber = index + 2; // account for header row
      const sellerName = normalizeValue(row.sellerName ?? row.seller_name);
      const sellerId = normalizeSellerId(row.sellerId ?? row.seller_id);
      const l4Category = normalizeValue(row.l4Category ?? row.l4_category);
      const commissionValue = row.commissionValue ?? row.commission_value;

      if (!sellerId || commissionValue === undefined || commissionValue === null || `${commissionValue}`.trim() === '' || !l4Category) {
        errors.push({ row: rowNumber, message: 'sellerId, l4Category and commissionValue are required' });
        continue;
      }

      const commissionNumber = Number(commissionValue);
      if (!Number.isFinite(commissionNumber)) {
        errors.push({ row: rowNumber, message: 'commission_value must be a valid number' });
        continue;
      }
      if (commissionNumber < 0) {
        errors.push({ row: rowNumber, message: 'commission_value must be non-negative' });
        continue;
      }

      if (!sellerIdSet.has(sellerId)) {
        errors.push({ row: rowNumber, message: 'Seller does not exist' });
        continue;
      }

      const commissionDetails = Object.entries(row).reduce((acc, [key, value]) => {
        if (knownColumns.has(key)) {
          return acc;
        }
        const normalized = normalizeValue(value);
        if (normalized !== undefined && normalized !== null && `${normalized}`.trim() !== '') {
          acc[key] = normalized;
        }
        return acc;
      }, {});
      const commissionDetailsPayload = Object.keys(commissionDetails).length ? commissionDetails : null;

      const compositeKey = toKey(sellerId, l4Category);
      const existingRecord = commissionByCompositeKey.get(compositeKey);
      const commissionPayload = {
        seller_name: sellerName,
        seller_id: sellerId,
        l4_category: l4Category,
        commission_value: commissionNumber,
        commission_details: commissionDetailsPayload
      };
      if (existingRecord) {
        commissionPayload.id = existingRecord.id; // allow updateOnDuplicate via PK
      }

      commissionUpsertMap.set(compositeKey, commissionPayload); // last occurrence for sellerId+l4Category wins
      successes.push({ row: rowNumber, record: commissionPayload });
    }

    if (!successes.length) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        message: 'No valid rows found in the uploaded file',
        errors
      });
    }

    const upserts = Array.from(commissionUpsertMap.values());
    if (upserts.length) {
      await SellerCommissionDetails.bulkCreate(upserts, {
        updateOnDuplicate: ['seller_name', 'commission_value', 'l4_category', 'commission_details', 'updated_at']
      });
    }

    const updatedRecords = upserts.length
      ? await SellerCommissionDetails.findAll({
          where: { seller_id: { [Op.in]: upserts.map(item => item.seller_id) } }
        })
      : [];

    return res.status(httpStatus.OK).json({
      status: true,
      message: `Processed ${successes.length} row(s)${errors.length ? `, ${errors.length} row(s) skipped` : ''}`,
      data: updatedRecords,
      errors
    });
  } catch (error) {
    global.logError(error);
    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      message: 'Error saving commission details',
      error: error.message
    });
  } finally {
    if (uploadedFilePath && fs.existsSync(uploadedFilePath)) {
      fs.unlink(uploadedFilePath, () => {});
    }
  }
});

const getCommissionDetailsBySellerId = catchAsync(async (req, res) => {
  const sellerId = req.query.seller_id ? `${req.query.seller_id}`.trim() : null;

  if (!sellerId) {
    return res.status(httpStatus.BAD_REQUEST).json({
      status: false,
      message: 'seller_id query parameter is required'
    });
  }

  const commissionDetails = await SellerCommissionDetails.findAll({
    where: { seller_id: sellerId },
    attributes: { exclude: ['id', 'created_at', 'updated_at'] },
    order: [['l4_category', 'ASC']]
  });

  if (!commissionDetails.length) {
    return res.status(httpStatus.NOT_FOUND).json({
      status: false,
      message: `Commission not found for this seller : ${sellerId}`
    });
  }

  return res.status(httpStatus.OK).json({
    status: true,
    data: commissionDetails
  });
});

module.exports = {
  upsertCommissionDetails,
  getCommissionDetailsBySellerId
};

