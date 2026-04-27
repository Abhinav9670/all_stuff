const catchAsync = require('../utils/catchAsync');
const httpStatus = require('http-status');
const { updateConsul, fetchConsul } = require('../helpers/consul');
const { addAdminLog } = require('../helpers/logging');
const { envMatch } = require('../consul-watch');
const { SellerConfig, sequelize } = require('../models/seqModels/index');
const { Op } = require('sequelize');

const consulMap = {
  oms: `oms/base_${process.env.NODE_ENV}`,
  app: `appConfig_${envMatch[process.env.NODE_ENV] || 'live'}`,
  java_order: `java/order-service/credentials_${
    envMatch[process.env.NODE_ENV] || 'live'
  }`,
  inventory: `inventory/${envMatch[process.env.NODE_ENV]}`,
  quote: `quote-service/base_config_${envMatch[process.env.NODE_ENV] || 'live'}`
};

const getConsulData = catchAsync(async (req, res) => {
  const type = req.params.type;
  let jsonResponse = {};
  if (type === 'app') {
    jsonResponse = global.config;
  } else if (type === 'oms') {
    jsonResponse = global.baseConfig;
  } else if (type === 'java_order') {
    jsonResponse = global.javaOrderServiceConfig;
  }

  if (type.includes('inventory')) {
    const keyArr = type.split('--');
    const parentKey = consulMap[keyArr[0].toString()];
    const response = await fetchConsul(`${parentKey}/${keyArr[1].toString()}`);
    jsonResponse = response;
  }

  res.status(httpStatus.OK).json({
    ...jsonResponse
  });
});

const saveConsulData = catchAsync(async (req, res) => {
  try {
    const { configType, data } = req.body;
    let consulKey = consulMap[configType];

    if (configType.includes('inventory')) {
      const keyArr = configType.split('--');
      const parentKey = consulMap[keyArr[0].toString()];
      consulKey = `${parentKey}/${keyArr[1].toString()}`;
    }

    if (!consulKey) {
      return res.status(400).json({ error: 'Invalid key' });
    }

    const response = await updateConsul(consulKey, data);

    let currentValue = {};
    if (configType === 'app') {
      currentValue = global.config;
    } else if (configType === 'oms') {
      currentValue = global.baseConfig;
    } else if (configType === 'java_order') {
      currentValue = global.javaOrderServiceConfig;
    }

    const logData = { consulKey, before: currentValue, after: data };
    // Log admin action asynchronously (non-blocking) to avoid timeout issues
    addAdminLog({
      type: 'configUpdate',
      data: logData,
      email: req.email,
      desc: 'Consul update'
    }).catch(err => {
      // Log error but don't block the response
      global.logError('Error saving admin log (non-blocking):', err);
    });

    return res.status(httpStatus.OK).json({
      status: response
    });
  } catch (e) {
    global.logError(e.message);
    return res.status(500).json({ error: e.message });
  }
});

const saveSellerDetail = catchAsync(async (req, res) => {
  try {
    const { data } = req.body;
    const { email } = req;

    // Validate required fields
    if (!data || !data.SELLER_ID) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'SELLER_ID is required',
        message: 'SELLER_ID is required'
      });
    }

    if (!data.styli_warehouse_id) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'styli_warehouse_id is required',
        message: 'styli_warehouse_id is required'
      });
    }

    if (!data.seller_warehouse_id) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'seller_warehouse_id is required',
        message: 'seller_warehouse_id is required'
      });
    }

    if (!data.seller_type) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'seller_type is required',
        message: 'seller_type is required'
      });
    }

    // Validate JSON fields
    if (!data.basic_settings || typeof data.basic_settings !== 'object') {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'basic_settings is required and must be a JSON object',
        message: 'basic_settings is required and must be a JSON object'
      });
    }

    if (!data.configuration || typeof data.configuration !== 'object') {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'configuration is required and must be a JSON object',
        message: 'configuration is required and must be a JSON object'
      });
    }

    if (!data.address || typeof data.address !== 'object') {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'address is required and must be a JSON object',
        message: 'address is required and must be a JSON object'
      });
    }

    // Check if the combination of styli_warehouse_id and seller_warehouse_id already exists
    const existingConfig = await SellerConfig.findOne({
      where: {
        styli_warehouse_id: data.styli_warehouse_id,
        seller_warehouse_id: data.seller_warehouse_id
      }
    });

    if (existingConfig) {
      return res.status(httpStatus.CONFLICT).json({
        status: false,
        error: 'This combination of Styli Warehouse ID and Seller Warehouse ID already exists',
        message: `The combination of styli_warehouse_id '${data.styli_warehouse_id}' and seller_warehouse_id '${data.seller_warehouse_id}' is already assigned to seller '${existingConfig.SELLER_ID}'`
      });
    }

    // Check if styli_warehouse_id exists with different seller_warehouse_id
    const existingStyliWarehouse = await SellerConfig.findOne({
      where: {
        styli_warehouse_id: data.styli_warehouse_id
      }
    });

    if (existingStyliWarehouse && existingStyliWarehouse.seller_warehouse_id !== data.seller_warehouse_id) {
      return res.status(httpStatus.CONFLICT).json({
        status: false,
        error: 'Styli Warehouse ID already mapped to different Seller Warehouse ID',
        message: `The styli_warehouse_id '${data.styli_warehouse_id}' is already mapped to seller_warehouse_id '${existingStyliWarehouse.seller_warehouse_id}' for seller '${existingStyliWarehouse.SELLER_ID}'. These warehouse IDs are mapped together and cannot be changed.`
      });
    }

    // Check if seller_warehouse_id exists with different styli_warehouse_id
    const existingSellerWarehouse = await SellerConfig.findOne({
      where: {
        seller_warehouse_id: data.seller_warehouse_id
      }
    });

    if (existingSellerWarehouse && existingSellerWarehouse.styli_warehouse_id !== data.styli_warehouse_id) {
      return res.status(httpStatus.CONFLICT).json({
        status: false,
        error: 'Seller Warehouse ID already mapped to different Styli Warehouse ID',
        message: `The seller_warehouse_id '${data.seller_warehouse_id}' is already mapped to styli_warehouse_id '${existingSellerWarehouse.styli_warehouse_id}' for seller '${existingSellerWarehouse.SELLER_ID}'. These warehouse IDs are mapped together and cannot be changed.`
      });
    }

    // Map the request data to database fields
    const sellerConfigData = {
      SELLER_ID: data.SELLER_ID,
      styli_warehouse_id: data.styli_warehouse_id,
      seller_warehouse_id: data.seller_warehouse_id,
      seller_type: data.seller_type,
      basic_settings: data.basic_settings,
      configuration: data.configuration,
      address: data.address,
      created_by: email || null,
      updated_by: email || null
    };

    // Create new config record
    const result = await SellerConfig.create(sellerConfigData);

    // Log admin action asynchronously (non-blocking) to avoid timeout issues
    addAdminLog({
      type: 'configUpdate',
      data: {
        SELLER_ID: sellerConfigData.SELLER_ID,
        after: sellerConfigData
      },
      email: email,
      desc: 'Seller detail config created'
    }).catch(err => {
      // Log error but don't block the response
      global.logError('Error saving admin log (non-blocking):', err);
    });

    // Format response
    const responseData = {
      id: result.id,
      SELLER_ID: result.SELLER_ID,
      styli_warehouse_id: result.styli_warehouse_id,
      seller_warehouse_id: result.seller_warehouse_id,
      seller_type: result.seller_type,
      basic_settings: result.basic_settings,
      configuration: result.configuration,
      address: result.address,
      created_at: result.created_at,
      updated_at: result.updated_at
    };

    return res.status(httpStatus.OK).json({
      status: true,
      data: responseData
    });
  } catch (e) {
    global.logError(e);
    
    // Handle unique constraint violations (composite key)
    if (e.name === 'SequelizeUniqueConstraintError') {
      const constraintName = e.errors[0]?.path || e.parent?.constraint;
      let message = 'Duplicate entry';
      
      if (constraintName === 'unique_warehouse_combination' || constraintName?.includes('warehouse_combination')) {
        message = `The combination of styli_warehouse_id '${req.body.data?.styli_warehouse_id}' and seller_warehouse_id '${req.body.data?.seller_warehouse_id}' already exists. These warehouse IDs are mapped together and must be unique as a pair.`;
      }
      
      return res.status(httpStatus.CONFLICT).json({
        status: false,
        error: e.errors[0]?.message || 'Duplicate entry',
        message: message
      });
    }

    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Error saving seller detail config',
      message: e.message || 'Error saving seller detail config'
    });
  }
});

const updateSellerDetail = catchAsync(async (req, res) => {
  try {
    const { sellerId } = req.params;
    const { data } = req.body;
    const { email } = req;

    if (!sellerId) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'sellerId is required in URL path',
        message: 'sellerId is required in URL path'
      });
    }

    if (!data || Object.keys(data).length === 0) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'No data provided for update',
        message: 'No data provided for update'
      });
    }

    // Check if seller config exists
    const existingConfig = await SellerConfig.findOne({
      where: { SELLER_ID: sellerId }
    });

    if (!existingConfig) {
      return res.status(httpStatus.NOT_FOUND).json({
        status: false,
        error: `Seller config with SELLER_ID ${sellerId} not found`,
        message: `Seller config with SELLER_ID ${sellerId} not found`
      });
    }

    // Build update data object
    const updateData = {
      updated_by: email || null
    };

    // Handle warehouse ID updates - validate the combination
    const newStyliWarehouseId = data.styli_warehouse_id !== undefined 
      ? data.styli_warehouse_id 
      : existingConfig.styli_warehouse_id;
    const newSellerWarehouseId = data.seller_warehouse_id !== undefined 
      ? data.seller_warehouse_id 
      : existingConfig.seller_warehouse_id;

    // If either warehouse ID is being changed, validate the new combination
    if (data.styli_warehouse_id !== undefined || data.seller_warehouse_id !== undefined) {
      // Check if the new combination already exists (excluding current record)
      const existingCombination = await SellerConfig.findOne({
        where: {
          styli_warehouse_id: newStyliWarehouseId,
          seller_warehouse_id: newSellerWarehouseId,
          id: { [Op.ne]: existingConfig.id }
        }
      });

      if (existingCombination) {
        return res.status(httpStatus.CONFLICT).json({
          status: false,
          error: 'This combination of Styli Warehouse ID and Seller Warehouse ID already exists',
          message: `The combination of styli_warehouse_id '${newStyliWarehouseId}' and seller_warehouse_id '${newSellerWarehouseId}' is already assigned to seller '${existingCombination.SELLER_ID}'`
        });
      }

      // Check if styli_warehouse_id exists with different seller_warehouse_id
      const existingStyliWarehouse = await SellerConfig.findOne({
        where: {
          styli_warehouse_id: newStyliWarehouseId,
          id: { [Op.ne]: existingConfig.id }
        }
      });

      if (existingStyliWarehouse && existingStyliWarehouse.seller_warehouse_id !== newSellerWarehouseId) {
        return res.status(httpStatus.CONFLICT).json({
          status: false,
          error: 'Styli Warehouse ID already mapped to different Seller Warehouse ID',
          message: `The styli_warehouse_id '${newStyliWarehouseId}' is already mapped to seller_warehouse_id '${existingStyliWarehouse.seller_warehouse_id}' for seller '${existingStyliWarehouse.SELLER_ID}'. These warehouse IDs are mapped together and cannot be changed.`
        });
      }

      // Check if seller_warehouse_id exists with different styli_warehouse_id
      const existingSellerWarehouse = await SellerConfig.findOne({
        where: {
          seller_warehouse_id: newSellerWarehouseId,
          id: { [Op.ne]: existingConfig.id }
        }
      });

      if (existingSellerWarehouse && existingSellerWarehouse.styli_warehouse_id !== newStyliWarehouseId) {
        return res.status(httpStatus.CONFLICT).json({
          status: false,
          error: 'Seller Warehouse ID already mapped to different Styli Warehouse ID',
          message: `The seller_warehouse_id '${newSellerWarehouseId}' is already mapped to styli_warehouse_id '${existingSellerWarehouse.styli_warehouse_id}' for seller '${existingSellerWarehouse.SELLER_ID}'. These warehouse IDs are mapped together and cannot be changed.`
        });
      }

      // If validation passes, add to update data
      if (data.styli_warehouse_id !== undefined) {
        updateData.styli_warehouse_id = data.styli_warehouse_id;
    }
    if (data.seller_warehouse_id !== undefined) {
      updateData.seller_warehouse_id = data.seller_warehouse_id;
    }
    }

    // Handle other field updates
    if (data.seller_type !== undefined) {
      updateData.seller_type = data.seller_type;
    }

    // Handle JSON field partial updates - merge with existing data
    if (data.basic_settings !== undefined) {
      const existingBasicSettings = existingConfig.basic_settings || {};
      updateData.basic_settings = {
        ...existingBasicSettings,
        ...data.basic_settings
      };
    }

    if (data.configuration !== undefined) {
      const existingConfiguration = existingConfig.configuration || {};
      updateData.configuration = {
        ...existingConfiguration,
        ...data.configuration
      };
    }

    if (data.address !== undefined) {
      const existingAddress = existingConfig.address || {};
      updateData.address = {
        ...existingAddress,
        ...data.address
      };
    }

    // Update the record
    await SellerConfig.update(updateData, {
      where: { SELLER_ID: sellerId }
    });

    // Fetch updated record
    const updatedConfig = await SellerConfig.findOne({
      where: { SELLER_ID: sellerId }
    });

    // Log admin action asynchronously (non-blocking) to avoid timeout issues
    addAdminLog({
      type: 'configUpdate',
      data: {
        SELLER_ID: sellerId,
        before: existingConfig.dataValues,
        after: updatedConfig.dataValues
      },
      email: email,
      desc: 'Seller detail config updated'
    }).catch(err => {
      // Log error but don't block the response
      global.logError('Error saving admin log (non-blocking):', err);
    });

    // Format response
    const responseData = {
      id: updatedConfig.id,
      SELLER_ID: updatedConfig.SELLER_ID,
      styli_warehouse_id: updatedConfig.styli_warehouse_id,
      seller_warehouse_id: updatedConfig.seller_warehouse_id,
      seller_type: updatedConfig.seller_type,
      basic_settings: updatedConfig.basic_settings,
      configuration: updatedConfig.configuration,
      address: updatedConfig.address,
      created_at: updatedConfig.created_at,
      updated_at: updatedConfig.updated_at
    };

    return res.status(httpStatus.OK).json({
      status: true,
      message: 'Seller Detail Config updated successfully!',
      data: responseData
    });
  } catch (e) {
    global.logError(e);
    
    // Handle unique constraint violations (composite key)
    if (e.name === 'SequelizeUniqueConstraintError') {
      const constraintName = e.errors[0]?.path || e.parent?.constraint;
      let message = 'Duplicate entry';
      
      if (constraintName === 'unique_warehouse_combination' || constraintName?.includes('warehouse_combination')) {
        message = `The combination of styli_warehouse_id and seller_warehouse_id already exists. These warehouse IDs are mapped together and must be unique as a pair.`;
      }
      
      return res.status(httpStatus.CONFLICT).json({
        status: false,
        error: e.errors[0]?.message || 'Duplicate entry',
        message: message
      });
    }

    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Error updating seller detail config',
      message: e.message || 'Error updating seller detail config'
    });
  }
});

const updateSellerDetailByWarehouse = catchAsync(async (req, res) => {
  try {
    const { data,warehouseId } = req.body;
    const { email } = req;

    if (!warehouseId) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'warehouseId is required in URL path',
        message: 'warehouseId is required in URL path'
      });
    }

    if (!data || Object.keys(data).length === 0) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'No data provided for update',
        message: 'No data provided for update'
      });
    }

    // Find config by styli_warehouse_id
    const existingConfig = await SellerConfig.findOne({
      where: { styli_warehouse_id: warehouseId }
    });

    if (!existingConfig) {
      return res.status(httpStatus.NOT_FOUND).json({
        status: false,
        error: `Seller config with styli_warehouse_id '${warehouseId}' not found`,
        message: `Seller config with styli_warehouse_id '${warehouseId}' not found`
      });
    }

    // Build update data object
    const updateData = {
      updated_by: email || null
    };

    // Handle SELLER_ID update
    if (data.SELLER_ID !== undefined) {
      updateData.SELLER_ID = data.SELLER_ID;
    }

    // Handle warehouse ID updates - validate the combination
    const newStyliWarehouseId = data.styli_warehouse_id !== undefined 
      ? data.styli_warehouse_id 
      : existingConfig.styli_warehouse_id;
    const newSellerWarehouseId = data.seller_warehouse_id !== undefined 
      ? data.seller_warehouse_id 
      : existingConfig.seller_warehouse_id;

    // If either warehouse ID is being changed, validate the new combination
    if (data.styli_warehouse_id !== undefined || data.seller_warehouse_id !== undefined) {
      // Check if the new combination already exists (excluding current record)
      const existingCombination = await SellerConfig.findOne({
        where: {
          styli_warehouse_id: newStyliWarehouseId,
          seller_warehouse_id: newSellerWarehouseId,
          id: { [Op.ne]: existingConfig.id }
        }
      });

      if (existingCombination) {
        return res.status(httpStatus.CONFLICT).json({
          status: false,
          error: 'This combination of Styli Warehouse ID and Seller Warehouse ID already exists',
          message: `The combination of styli_warehouse_id '${newStyliWarehouseId}' and seller_warehouse_id '${newSellerWarehouseId}' is already assigned to seller '${existingCombination.SELLER_ID}'`
        });
      }

      // Check if styli_warehouse_id exists with different seller_warehouse_id
      const existingStyliWarehouse = await SellerConfig.findOne({
        where: {
          styli_warehouse_id: newStyliWarehouseId,
          id: { [Op.ne]: existingConfig.id }
        }
      });

      if (existingStyliWarehouse && existingStyliWarehouse.seller_warehouse_id !== newSellerWarehouseId) {
        return res.status(httpStatus.CONFLICT).json({
          status: false,
          error: 'Styli Warehouse ID already mapped to different Seller Warehouse ID',
          message: `The styli_warehouse_id '${newStyliWarehouseId}' is already mapped to seller_warehouse_id '${existingStyliWarehouse.seller_warehouse_id}' for seller '${existingStyliWarehouse.SELLER_ID}'. These warehouse IDs are mapped together and cannot be changed.`
        });
      }

      // Check if seller_warehouse_id exists with different styli_warehouse_id
      const existingSellerWarehouse = await SellerConfig.findOne({
        where: {
          seller_warehouse_id: newSellerWarehouseId,
          id: { [Op.ne]: existingConfig.id }
        }
      });

      if (existingSellerWarehouse && existingSellerWarehouse.styli_warehouse_id !== newStyliWarehouseId) {
        return res.status(httpStatus.CONFLICT).json({
          status: false,
          error: 'Seller Warehouse ID already mapped to different Styli Warehouse ID',
          message: `The seller_warehouse_id '${newSellerWarehouseId}' is already mapped to styli_warehouse_id '${existingSellerWarehouse.styli_warehouse_id}' for seller '${existingSellerWarehouse.SELLER_ID}'. These warehouse IDs are mapped together and cannot be changed.`
        });
      }

      // If validation passes, add to update data
      if (data.styli_warehouse_id !== undefined) {
        updateData.styli_warehouse_id = data.styli_warehouse_id;
      }
      if (data.seller_warehouse_id !== undefined) {
        updateData.seller_warehouse_id = data.seller_warehouse_id;
      }
    }

    // Handle other field updates
    if (data.seller_type !== undefined) {
      updateData.seller_type = data.seller_type;
    }

    // Handle JSON field partial updates - merge with existing data
    if (data.basic_settings !== undefined) {
      const existingBasicSettings = existingConfig.basic_settings || {};
      updateData.basic_settings = {
        ...existingBasicSettings,
        ...data.basic_settings
      };
    }

    if (data.configuration !== undefined) {
      const existingConfiguration = existingConfig.configuration || {};
      updateData.configuration = {
        ...existingConfiguration,
        ...data.configuration
      };
    }

    if (data.address !== undefined) {
      const existingAddress = existingConfig.address || {};
      updateData.address = {
        ...existingAddress,
        ...data.address
      };
    }

    // Update the record using styli_warehouse_id
    await SellerConfig.update(updateData, {
      where: { styli_warehouse_id: warehouseId }
    });

    // Log admin action asynchronously (non-blocking) to avoid timeout issues
    addAdminLog({
      type: 'configUpdate',
      data: {
        styli_warehouse_id: warehouseId,
        before: existingConfig.dataValues,
        after: updateData
      },
      email: email,
      desc: 'Seller detail config updated by warehouse'
    }).catch(err => {
      // Log error but don't block the response
      global.logError('Error saving admin log (non-blocking):', err);
    });

    return res.status(httpStatus.OK).json({
      status: true,
      message: 'Seller Detail Config updated successfully!'
    });
  } catch (e) {
    global.logError(e);
    
    // Handle unique constraint violations (composite key)
    if (e.name === 'SequelizeUniqueConstraintError') {
      const constraintName = e.errors[0]?.path || e.parent?.constraint;
      let message = 'Duplicate entry';
      
      if (constraintName === 'unique_warehouse_combination' || constraintName?.includes('warehouse_combination')) {
        message = `The combination of styli_warehouse_id and seller_warehouse_id already exists. These warehouse IDs are mapped together and must be unique as a pair.`;
      }
      
      return res.status(httpStatus.CONFLICT).json({
        status: false,
        error: e.errors[0]?.message || 'Duplicate entry',
        message: message
      });
    }

    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Error updating seller detail config',
      message: e.message || 'Error updating seller detail config'
    });
  }
});

const getSellerDetail = catchAsync(async (req, res) => {
  try {
    const { sellerId } = req.params;

    if (!sellerId) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'sellerId is required in URL path',
        message: 'sellerId is required in URL path'
      });
    }

    const config = await SellerConfig.findOne({
      where: { SELLER_ID: sellerId }
    });

    if (!config) {
      return res.status(httpStatus.NOT_FOUND).json({
        status: false,
        error: `Seller config with SELLER_ID ${sellerId} not found`,
        message: `Seller config with SELLER_ID ${sellerId} not found`
      });
    }

    // Format response
    const responseData = {
      id: config.id,
      SELLER_ID: config.SELLER_ID,
      styli_warehouse_id: config.styli_warehouse_id,
      seller_warehouse_id: config.seller_warehouse_id,
      seller_type: config.seller_type,
      basic_settings: config.basic_settings,
      configuration: config.configuration,
      address: config.address,
      created_at: config.created_at,
      updated_at: config.updated_at
    };

    return res.status(httpStatus.OK).json({
      status: true,
      data: responseData
    });
  } catch (e) {
    global.logError(e);
    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Error fetching seller detail config',
      message: e.message || 'Error fetching seller detail config'
    });
  }
});

const getSellerDetailByWarehouse = catchAsync(async (req, res) => {
  try {
    const { warehouseId } = req.body; // This is styli_warehouse_id

    if (!warehouseId) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'Invalid warehouse ID',
        message: 'Warehouse ID is required'
      });
    }

    const config = await SellerConfig.findOne({
      where: { styli_warehouse_id: warehouseId }
    });

    if (!config) {
      return res.status(httpStatus.NOT_FOUND).json({
        status: false,
        error: 'Configuration not found',
        message: `No seller detail configuration found for warehouse ID: ${warehouseId}`
      });
    }

    // Format response
    const responseData = {
      id: config.id,
      SELLER_ID: config.SELLER_ID,
      styli_warehouse_id: config.styli_warehouse_id,
      seller_warehouse_id: config.seller_warehouse_id,
      seller_type: config.seller_type,
      basic_settings: config.basic_settings,
      configuration: config.configuration,
      address: config.address,
      created_at: config.created_at,
      updated_at: config.updated_at
    };

    return res.status(httpStatus.OK).json({
      status: true,
      data: responseData
    });
  } catch (e) {
    global.logError(e);
    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Error fetching seller detail config',
      message: e.message || 'Error fetching seller detail config'
    });
  }
});

const checkSellerDetailExists = catchAsync(async (req, res) => {
  try {
    const { seller_id, warehouse_id } = req.query;

    // Validate that at least one parameter is provided
    if (!seller_id && !warehouse_id) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'Missing required parameters: seller_id or warehouse_id',
        exists: false
      });
    }

    // Build where clause
    const whereClause = {};
    if (seller_id) {
      whereClause.SELLER_ID = seller_id;
    }
    if (warehouse_id) {
      whereClause.styli_warehouse_id = warehouse_id;
    }

    // Check if config exists
    const config = await SellerConfig.findOne({
      where: whereClause,
      attributes: ['SELLER_ID', 'styli_warehouse_id']
    });

    if (config) {
      return res.status(httpStatus.OK).json({
        status: true,
        exists: true,
        message: 'Configuration already exists',
        data: {
          SELLER_ID: config.SELLER_ID,
          styli_warehouse_id: config.styli_warehouse_id
        }
      });
    } else {
      return res.status(httpStatus.OK).json({
        status: true,
        exists: false,
        message: 'Configuration does not exist'
      });
    }
  } catch (e) {
    global.logError(e);
    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Error checking seller detail config existence',
      exists: false
    });
  }
});

const getSellerDetailConfigList = catchAsync(async (req, res) => {
  try {
    const { filters = {}, offset = 0, pageSize = 20, query = '' } = req.body;

    // Build Sequelize where clause
    const whereClause = {};

    // Filter by SELLER_ID (exact match)
    if (filters.SELLER_ID) {
      whereClause.SELLER_ID = String(filters.SELLER_ID);
    }

    // Filter by seller_type
    if (filters.seller_type) {
      whereClause.seller_type = filters.seller_type;
    }

    // Filter by styli_warehouse_id
    if (filters.styli_warehouse_id) {
      whereClause.styli_warehouse_id = {
        [Op.like]: `%${filters.styli_warehouse_id}%`
      };
    }

    // Filter by seller_warehouse_id
    if (filters.seller_warehouse_id) {
      whereClause.seller_warehouse_id = {
        [Op.like]: `%${filters.seller_warehouse_id}%`
      };
    }

    // Search in basic_settings JSON (seller_name, country_code, etc.)
    if (query) {
      whereClause[Op.or] = [
        { SELLER_ID: { [Op.like]: `%${query}%` } },
        { styli_warehouse_id: { [Op.like]: `%${query}%` } },
        { seller_warehouse_id: { [Op.like]: `%${query}%` } },
        sequelize.where(
          sequelize.fn('JSON_EXTRACT', sequelize.col('basic_settings'), '$.seller_name'),
          { [Op.like]: `%${query}%` }
        )
      ];
}

    // Convert offset and pageSize to numbers
    const offsetNum = parseInt(offset, 10) || 0;
    const pageSizeNum = parseInt(pageSize, 10) || 20;

    // Get total count for pagination
    const totalCount = await SellerConfig.count({ where: whereClause });

    // Fetch paginated results
    const configs = await SellerConfig.findAll({
      where: whereClause,
      limit: pageSizeNum,
      offset: offsetNum,
      order: [['created_at', 'DESC']]
    });

    // Format response
    const formattedConfigs = configs.map(config => ({
      id: config.id,
      SELLER_ID: config.SELLER_ID,
      styli_warehouse_id: config.styli_warehouse_id,
      seller_warehouse_id: config.seller_warehouse_id,
      seller_type: config.seller_type,
      basic_settings: config.basic_settings,
      configuration: config.configuration,
      address: config.address,
      created_at: config.created_at,
      updated_at: config.updated_at
    }));

    return res.status(httpStatus.OK).json({
      status: true,
      data: {
        configs: formattedConfigs,
        totalCount: totalCount
      }
    });
  } catch (e) {
    global.logError(e);
    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Error fetching seller detail config list',
      message: e.message || 'Error fetching seller detail config list'
    });
  }
});

// Helper function to get value from multiple possible keys
function getValue(consulConfig, ...keys) {
  for (const key of keys) {
    if (consulConfig[key] !== undefined && consulConfig[key] !== null && consulConfig[key] !== '') {
      return consulConfig[key];
    }
  }
  return undefined;
}

// Helper function to transform Consul config to DB schema for migration
// Only includes fields that exist in the source data
function transformConsulToDbSchema(consulConfig, createdBy) {
  // Determine seller type
  let sellerType = 'seller_central_luna';
  if (consulConfig.UC_BASE_URL || consulConfig.CLIENT_ID || consulConfig.MERCHANT_ID) {
    sellerType = 'unicommerce';
  } else if (consulConfig.PUSH_TO_WMS === true && consulConfig.PUSH_TO_SELLER_CENTRAL !== true) {
    sellerType = 'wms';
  }

  // Extract basic settings - only include fields that exist in source
  const basicSettings = {};
  
  // Boolean fields (include if exists)
  if (consulConfig.PUSH_TO_WMS !== undefined) {
    basicSettings.PUSH_TO_WMS = consulConfig.PUSH_TO_WMS;
  }
  if (consulConfig.PUSH_ORDER_FOR_SKU !== undefined) {
    basicSettings.PUSH_ORDER_FOR_SKU = consulConfig.PUSH_ORDER_FOR_SKU;
  }
  if (consulConfig.PUSH_TO_SELLER_CENTRAL !== undefined) {
    basicSettings.PUSH_TO_SELLER_CENTRAL = consulConfig.PUSH_TO_SELLER_CENTRAL;
  }
  if (consulConfig.HAS_GLOBAL_SHIPMENT !== undefined) {
    basicSettings.HAS_GLOBAL_SHIPMENT = consulConfig.HAS_GLOBAL_SHIPMENT;
  }
  
  // String fields - only include if they exist
  const countryCode = getValue(consulConfig, 'country_code', 'COUNTRY_CODE');
  if (countryCode !== undefined) {
    basicSettings.country_code = countryCode;
  }
  
  const sellerName = getValue(consulConfig, 'seller_name', 'SELLER_NAME');
  if (sellerName !== undefined) {
    basicSettings.seller_name = sellerName;
  }
  
  if (consulConfig.warehouse_id !== undefined) {
    basicSettings.warehouse_id = consulConfig.warehouse_id;
  }
  
  const warehouseName = getValue(consulConfig, 'warehouse_name', 'WAREHOUSE_NAME');
  if (warehouseName !== undefined) {
    basicSettings.warehouse_name = warehouseName;
  }
  
  if (consulConfig.default_ship_to !== undefined || consulConfig.DEFAULT_SHIP_TO !== undefined) {
    basicSettings.default_ship_to = consulConfig.default_ship_to || consulConfig.DEFAULT_SHIP_TO;
  }
  
  if (consulConfig.default_ship_to_warehouse_id !== undefined) {
    basicSettings.default_ship_to_warehouse_id = consulConfig.default_ship_to_warehouse_id;
  }
  
  const defaultFulfillmentBy = getValue(consulConfig, 'default_fullfilment_by', 'DEFAULT_FULFILLMENT_BY');
  if (defaultFulfillmentBy !== undefined) {
    basicSettings.default_fullfilment_by = defaultFulfillmentBy;
  }

  // Extract configuration settings - only include fields that exist
  const configuration = {};
  
  // WMS fields
  if (consulConfig.WMS_WAREHOUSE_HEADER_USER_NAME !== undefined) {
    configuration.WMS_WAREHOUSE_HEADER_USER_NAME = consulConfig.WMS_WAREHOUSE_HEADER_USER_NAME;
  }
  if (consulConfig.WMS_WAREHOUSE_HEADER_PASSWORD !== undefined) {
    configuration.WMS_WAREHOUSE_HEADER_PASSWORD = consulConfig.WMS_WAREHOUSE_HEADER_PASSWORD;
  }
  if (consulConfig.WMS_WAREHOUSE_BASE_URL !== undefined) {
    configuration.WMS_WAREHOUSE_BASE_URL = consulConfig.WMS_WAREHOUSE_BASE_URL;
  }
  if (consulConfig.WMS_WAREHOUSE_ORDER_CANCEL !== undefined) {
    configuration.WMS_WAREHOUSE_ORDER_CANCEL = consulConfig.WMS_WAREHOUSE_ORDER_CANCEL;
  }
  if (consulConfig.WMS_WAREHOUSE_OUTWARD_ORDER !== undefined) {
    configuration.WMS_WAREHOUSE_OUTWARD_ORDER = consulConfig.WMS_WAREHOUSE_OUTWARD_ORDER;
  }
  
  // Unicommerce fields
  if (consulConfig.CLIENT_ID !== undefined) {
    configuration.CLIENT_ID = consulConfig.CLIENT_ID;
  }
  if (consulConfig.MERCHANT_ID !== undefined) {
    configuration.MERCHANT_ID = consulConfig.MERCHANT_ID;
  }
  if (consulConfig.SECURITY_KEY !== undefined) {
    configuration.SECURITY_KEY = consulConfig.SECURITY_KEY;
  }
  if (consulConfig.UC_BASE_URL !== undefined) {
    configuration.UC_BASE_URL = consulConfig.UC_BASE_URL;
  }
  
  // Luna fields
  if (consulConfig.LUNA_MARKETPLACE_ID !== undefined) {
    configuration.LUNA_MARKETPLACE_ID = consulConfig.LUNA_MARKETPLACE_ID;
  }
  if (consulConfig.LUNA_SELLER_CENTRAL_ACCESS_KEY !== undefined) {
    configuration.LUNA_SELLER_CENTRAL_ACCESS_KEY = consulConfig.LUNA_SELLER_CENTRAL_ACCESS_KEY;
  }
  if (consulConfig.LUNA_SELLER_CENTRAL_SECRET_KEY !== undefined) {
    configuration.LUNA_SELLER_CENTRAL_SECRET_KEY = consulConfig.LUNA_SELLER_CENTRAL_SECRET_KEY;
  }
  
  // SLA fields - only include if they exist in source
  if (consulConfig.ACKNOWLEDGEMENT_SLA_HRS !== undefined) {
    configuration.ACKNOWLEDGEMENT_SLA_HRS = consulConfig.ACKNOWLEDGEMENT_SLA_HRS;
  }
  if (consulConfig.MAX_ACKNOWLEDGEMENT_BUFFER !== undefined) {
    configuration.MAX_ACKNOWLEDGEMENT_BUFFER = consulConfig.MAX_ACKNOWLEDGEMENT_BUFFER;
  }
  if (consulConfig.PACKED_SLA_HRS !== undefined) {
    configuration.PACKED_SLA_HRS = consulConfig.PACKED_SLA_HRS;
  }
  if (consulConfig.MAX_PACKED_BUFFER !== undefined) {
    configuration.MAX_PACKED_BUFFER = consulConfig.MAX_PACKED_BUFFER;
  }
  if (consulConfig.SHIPPED_SLA_HRS !== undefined) {
    configuration.SHIPPED_SLA_HRS = consulConfig.SHIPPED_SLA_HRS;
  }
  if (consulConfig.MAX_SHIPPED_BUFFER !== undefined) {
    configuration.MAX_SHIPPED_BUFFER = consulConfig.MAX_SHIPPED_BUFFER;
  }
  
  // Other configuration fields
  if (consulConfig.order_status_governance !== undefined) {
    configuration.order_status_governance = consulConfig.order_status_governance;
  }
  if (consulConfig.PICKUP_INFO_NAME !== undefined) {
    configuration.PICKUP_INFO_NAME = consulConfig.PICKUP_INFO_NAME;
  }
  if (consulConfig.RETURN_INFO_NAME !== undefined) {
    configuration.RETURN_INFO_NAME = consulConfig.RETURN_INFO_NAME;
  }

  // Extract address settings - check for both ADDRESS and INFO naming conventions
  // Note: address field is NOT NULL in DB, so we always include it (even if empty)
  const address = {};
  
  // Check for PICKUP_ADDRESS_* (from Consul) or PICKUP_INFO_* (alternative naming)
  const pickupEn = consulConfig.PICKUP_ADDRESS_EN || consulConfig.PICKUP_INFO_EN;
  if (pickupEn !== undefined && Array.isArray(pickupEn) && pickupEn.length > 0) {
    address.PICKUP_INFO_EN = pickupEn;
  }
  
  const pickupAr = consulConfig.PICKUP_ADDRESS_AR || consulConfig.PICKUP_INFO_AR;
  if (pickupAr !== undefined && Array.isArray(pickupAr) && pickupAr.length > 0) {
    address.PICKUP_INFO_AR = pickupAr;
  }
  
  // Check for DROPOFF_ADDRESS_* (from Consul) or DROPOFF_INFO_* (alternative naming)
  const dropoffEn = consulConfig.DROPOFF_ADDRESS_EN || consulConfig.DROPOFF_INFO_EN;
  if (dropoffEn !== undefined && Array.isArray(dropoffEn) && dropoffEn.length > 0) {
    address.DROPOFF_INFO_EN = dropoffEn;
  }
  
  const dropoffAr = consulConfig.DROPOFF_ADDRESS_AR || consulConfig.DROPOFF_INFO_AR;
  if (dropoffAr !== undefined && Array.isArray(dropoffAr) && dropoffAr.length > 0) {
    address.DROPOFF_INFO_AR = dropoffAr;
  }
  
  // Check for INVOICE_ADDRESS_* (both naming conventions)
  if (consulConfig.INVOICE_ADDRESS_EN !== undefined && Array.isArray(consulConfig.INVOICE_ADDRESS_EN) && consulConfig.INVOICE_ADDRESS_EN.length > 0) {
    address.INVOICE_ADDRESS_EN = consulConfig.INVOICE_ADDRESS_EN;
  }
  if (consulConfig.INVOICE_ADDRESS_AR !== undefined && Array.isArray(consulConfig.INVOICE_ADDRESS_AR) && consulConfig.INVOICE_ADDRESS_AR.length > 0) {
    address.INVOICE_ADDRESS_AR = consulConfig.INVOICE_ADDRESS_AR;
  }

  // Build return object
  // Note: basic_settings, configuration, and address are all NOT NULL in DB,
  // so we always include them (even if empty objects)
  const result = {
    SELLER_ID: String(consulConfig.SELLER_ID || consulConfig.seller_id || ''),
    styli_warehouse_id: String(consulConfig.warehouse_id || consulConfig.styli_warehouse_id || ''),
    seller_warehouse_id: String(consulConfig.seller_warehouse_id || consulConfig.warehouse_id || ''),
    seller_type: sellerType,
    basic_settings: basicSettings,  // Always include (NOT NULL constraint)
    configuration: configuration,   // Always include (NOT NULL constraint)
    address: address,                // Always include (NOT NULL constraint)
    created_by: createdBy || 'migration_api',
    updated_by: createdBy || 'migration_api'
  };

  return result;
}

/**
 * Migrate seller_inventory_mapping from Consul to seller_config database table
 * POST /v1/config/migrate-seller-inventory
 * Body: {
 *   dryRun: true/false,
 *   offset: number (default: 0),
 *   limit: number (default: 50, max: 100)
 * }
 */
const migrateSellerInventoryToDb = catchAsync(async (req, res) => {
  try {
    const { email } = req;
    const {
      dryRun = true,
      offset = 0,
      limit = 50
    } = req.body;

    // Validate pagination parameters
    const offsetNum = parseInt(offset, 10) || 0;
    const limitNum = Math.min(parseInt(limit, 10) || 50, 100); // Max 100 per request
    const skip = Math.max(offsetNum, 0); // Ensure non-negative

    console.log(`[Migration] Started by: ${email}, dryRun: ${dryRun}`);
    console.log(`[Migration] Pagination: offset=${skip}, limit=${limitNum}`);
    console.log(`[Migration] NODE_ENV: ${process.env.NODE_ENV}`);
    console.log(`[Migration] javaOrderServiceConfig exists: ${!!global.javaOrderServiceConfig}`);
    console.log(`[Migration] javaOrderServiceConfig keys: ${global.javaOrderServiceConfig ? Object.keys(global.javaOrderServiceConfig).join(', ') : 'N/A'}`);

    // Get seller_inventory_mapping from Consul (already loaded in global)
    const sellerInventoryMapping = global.javaOrderServiceConfig?.seller_inventory_mapping;

    if (!sellerInventoryMapping || !Array.isArray(sellerInventoryMapping)) {
      return res.status(httpStatus.BAD_REQUEST).json({
        status: false,
        error: 'seller_inventory_mapping not found in Consul config',
        message: 'seller_inventory_mapping not found or is not an array',
        debug: {
          nodeEnv: process.env.NODE_ENV,
          javaOrderServiceConfigExists: !!global.javaOrderServiceConfig,
          availableKeys: global.javaOrderServiceConfig ? Object.keys(global.javaOrderServiceConfig) : []
        }
      });
    }

    const totalRecords = sellerInventoryMapping.length;
    const endIndex = Math.min(skip + limitNum, totalRecords);
    const recordsToProcess = sellerInventoryMapping.slice(skip, endIndex);
    const hasMore = endIndex < totalRecords;
    const nextOffset = hasMore ? endIndex : null;

    console.log(`[Migration] Total records: ${totalRecords}`);
    console.log(`[Migration] Processing records ${skip} to ${endIndex - 1} (${recordsToProcess.length} records)`);
    console.log(`[Migration] Has more records: ${hasMore}`);

    const results = {
      total: totalRecords,
      processed: {
        offset: skip,
        limit: limitNum,
        count: recordsToProcess.length,
        range: `${skip} to ${endIndex - 1}`
      },
      success: 0,
      skipped: 0,
      errors: 0,
      details: [],
      pagination: {
        currentOffset: skip,
        currentLimit: limitNum,
        totalRecords: totalRecords,
        processedRecords: endIndex,
        remainingRecords: Math.max(0, totalRecords - endIndex),
        hasMore: hasMore,
        nextOffset: nextOffset
      }
    };

    // Process only the slice of records
    for (const consulConfig of recordsToProcess) {
      const warehouseId = consulConfig.warehouse_id || consulConfig.styli_warehouse_id;
      const sellerId = consulConfig.SELLER_ID || consulConfig.seller_id;

      try {
        // Check if already exists
        const existing = await SellerConfig.findOne({
          where: {
            styli_warehouse_id: String(warehouseId),
            seller_warehouse_id: String(consulConfig.seller_warehouse_id || warehouseId)
          }
        });

        if (existing) {
          results.skipped++;
          results.details.push({
            warehouseId,
            sellerId,
            status: 'skipped',
            reason: 'Already exists'
          });
          continue;
        }

        // Transform consul config to DB schema
        const dbData = transformConsulToDbSchema(consulConfig, email);

        // Validate required fields
        if (!dbData.SELLER_ID || !dbData.styli_warehouse_id || !dbData.seller_warehouse_id) {
          results.skipped++;
          results.details.push({
            warehouseId,
            sellerId,
            status: 'skipped',
            reason: 'Missing required fields'
          });
          continue;
        }

        if (dryRun) {
          results.success++;
          results.details.push({
            warehouseId,
            sellerId,
            status: 'would_migrate',
            seller_type: dbData.seller_type,
            data: dbData
          });
        } else {
          await SellerConfig.create(dbData);
          results.success++;
          results.details.push({
            warehouseId,
            sellerId,
            status: 'migrated',
            seller_type: dbData.seller_type
          });
        }

      } catch (error) {
        results.errors++;
        results.details.push({
          warehouseId,
          sellerId,
          status: 'error',
          error: error.message
        });
      }
    }

    // Log admin action
    addAdminLog({
      type: 'migration',
      data: {
        dryRun,
        offset: skip,
        limit: limitNum,
        results: {
          total: results.total,
          processed: results.processed.count,
          success: results.success,
          skipped: results.skipped,
          errors: results.errors
        }
      },
      email: email,
      desc: dryRun
        ? `Seller config migration dry run (offset: ${skip}, limit: ${limitNum})`
        : `Seller config migration executed (offset: ${skip}, limit: ${limitNum})`
    }).catch(err => {
      global.logError('Error saving admin log:', err);
    });

    const message = hasMore
      ? `Processed ${recordsToProcess.length} records. ${results.pagination.remainingRecords} remaining. Use offset=${nextOffset} for next batch.`
      : dryRun
        ? 'Dry run completed - no data was inserted'
        : 'Migration completed - all records processed';

    return res.status(httpStatus.OK).json({
      status: true,
      message: message,
      dryRun,
      results
    });

  } catch (e) {
    global.logError(e);
    return res.status(httpStatus.INTERNAL_SERVER_ERROR).json({
      status: false,
      error: e.message || 'Migration failed',
      message: e.message || 'Migration failed'
    });
  }
});

module.exports = {
  getConsulData,
  saveConsulData,
  saveSellerDetail,
  updateSellerDetail,
  updateSellerDetailByWarehouse,
  getSellerDetail,
  getSellerDetailByWarehouse,
  checkSellerDetailExists,
  getSellerDetailConfigList,
  migrateSellerInventoryToDb
};
