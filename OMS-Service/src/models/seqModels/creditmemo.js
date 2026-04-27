const { DataTypes } = require('sequelize');
const columns = {
  adjustment: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: false,
    defaultValue: null
  },
  adjustment_negative: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: true,
    defaultValue: null
  },
  adjustment_positive: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: true,
    defaultValue: null
  },
  shipping_amount: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: false,
    defaultValue: null
  },
  tax_amount: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: true,
    defaultValue: null
  },
  amstorecredit_amount: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: true,
    defaultValue: null
  },
  discount_amount: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: true,
    defaultValue: null
  },
  cash_on_delivery_fee: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: true,
    defaultValue: null
  },
  order_currency_code: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  increment_id: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  created_at: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  rma_number: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  store_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: null
  },
  eas_coins: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: null
  },
  eas_value_in_currency: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: false,
    defaultValue: null
  },
  eas_value_in_base_currency: {
    type: DataTypes.DECIMAL(10, 4),
    allowNull: false,
    defaultValue: null
  },
  shukran_points_refunded: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: false,
    defaultValue: null
  },
  shukran_points_refunded_value_in_currency: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: false,
    defaultValue: null
  },
  shukran_points_refunded_value_in_base_currency: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: false,
    defaultValue: null
  },
  zatca_qr_code: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  zatca_status: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  sms_money: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: false,
    defaultValue: 0.0
  },
  memo_type: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
};
module.exports = function (sequelize, DataTypes) {
  const Creditmemo = sequelize.define(
    'Creditmemo',
    {
      entity_id: {
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
      subtotal_incl_tax: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      subtotal: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      grand_total: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      ...columns
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_creditmemo',
      timestamps: false
    }
  );

  Creditmemo.associate = function (models) {
    Creditmemo.belongsTo(models.Order, { foreignKey: 'entity_id' });
    Creditmemo.hasMany(models.CreditmemoItem, {
      foreignKey: 'parent_id',
      rejectOnEmpty: false
    });
    Creditmemo.hasMany(models.CreditmemoComment, {
      foreignKey: 'parent_id',
      rejectOnEmpty: false
    });
  };
  return Creditmemo;
};
