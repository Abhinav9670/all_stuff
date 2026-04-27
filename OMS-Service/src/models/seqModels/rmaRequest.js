const { DataTypes } = require('sequelize');
const columns = {
  created_at: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  modified_at: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  status: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: null
  },
  customer_id: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  },
  is_short_pickedup: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  },
  is_fraud_pickedup: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  },
  customer_name: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  rma_inc_id: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  shipping_label: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  is_created_by_admin: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  return_inc_payfort_id: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  return_fee: {
    type: DataTypes.DECIMAL,
    allowNull: true,
    defaultValue: null
  },
  return_invoice_amount: {
    type: DataTypes.DECIMAL,
    allowNull: true,
    defaultValue: 0.0
  },
  zatca_details: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  zatca_qr_code:{
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  }
};

module.exports = function (sequelize, DataTypes) {
  const RmaRequest = sequelize.define(
    'RmaRequest',
    {
      request_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      store_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      shukran_rt_successful: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      ...columns
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_rma_request',
      timestamps: false
    }
  );
  // console.log('type ', typeof Shipment);
  RmaRequest.associate = function (models) {
    RmaRequest.hasMany(models.RmaRequestItem, {
      foreignKey: 'request_id',
      rejectOnEmpty: false
    });
    RmaRequest.hasMany(models.RmaTracking, {
      foreignKey: 'request_id',
      rejectOnEmpty: false
    });
    RmaRequest.hasMany(models.RmaRequestStatusHistory, {
      foreignKey: 'request_id',
      rejectOnEmpty: false
    });
    RmaRequest.belongsTo(models.Order, { foreignKey: 'order_id' });
  };

  return RmaRequest;
};
