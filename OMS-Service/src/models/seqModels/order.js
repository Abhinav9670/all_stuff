const { DataTypes } = require('sequelize');
const columns = {
  order_currency_code: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  store_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: null
  },
  state: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  customer_email: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  wms_status: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: null
  },
  status: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  clickpost_message: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  delivered_at: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  source: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  estimated_delivery_time: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  grand_total: {
    type: DataTypes.DECIMAL(20, 4),
    allowNull: true,
    defaultValue: null
  },
  amstorecredit_amount: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: true,
    defaultValue: null
  },
  shipping_amount: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: true,
    defaultValue: null
  },
  cash_on_delivery_fee: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: false,
    defaultValue: 0.0
  },
  import_fee: {
    type: DataTypes.DECIMAL(12, 4),
    allowNull: true,
    defaultValue: null
  }
};
module.exports = function (sequelize, DataTypes) {
  const Order = sequelize.define(
    'Order',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      increment_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      }, retry_payment: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      customer_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      created_at: {
        type: 'TIMESTAMP',
        defaultValue: sequelize.fn('NOW')
      },
      updated_at: {
        type: 'TIMESTAMP',
        defaultValue: sequelize.fn('NOW')
      },
      ...columns
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order'
    }
  );
  // console.log('type ', typeof Shipment);
  Order.associate = function (models) {
    Order.hasMany(models.Shipment, {
      foreignKey: 'order_id',
      rejectOnEmpty: false
    });
    Order.hasMany(models.OrderItem, {
      foreignKey: 'order_id',
      rejectOnEmpty: false
    });
    Order.hasMany(models.OrderAddress, {
      foreignKey: 'parent_id',
      rejectOnEmpty: false
    });
    Order.hasMany(models.OrderPayment, {
      foreignKey: 'parent_id',
      rejectOnEmpty: false
    });
    Order.hasMany(models.Creditmemo, {
      foreignKey: 'order_id',
      rejectOnEmpty: false
    });
    Order.hasMany(models.RmaRequest, {
      foreignKey: 'order_id',
      rejectOnEmpty: false
    });
    Order.hasMany(models.SubSalesOrder, {
      foreignKey: 'order_id',
      rejectOnEmpty: false
    });

    // Associations for split orders
    Order.hasMany(models.SplitSellerOrderItem, {
      foreignKey: 'main_order_id',
      sourceKey: 'entity_id',
      as: 'SplitSellerOrderItems',
      rejectOnEmpty: false
    });

    Order.hasMany(models.SplitSellerOrderPayment, {
      foreignKey: 'main_order_id',
      sourceKey: 'entity_id',
      as: 'SplitSellerOrderPayments',
      rejectOnEmpty: false
    });

    Order.hasMany(models.SplitSubSellerOrder, {
      foreignKey: 'main_order_id',
      sourceKey: 'entity_id',
      as: 'SplitSubSellerOrders',
      rejectOnEmpty: false
    });
  };

  return Order;
};
