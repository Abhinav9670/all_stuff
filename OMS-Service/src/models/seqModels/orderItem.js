module.exports = (sequelize, DataTypes) => {
  const OrderItem = sequelize.define(
    'OrderItem',
    {
      item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      parent_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      qty_ordered: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      qty_shipped: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      qty_canceled: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      qty_refunded: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      product_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      sku: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      shukran_l4_category: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      description: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      original_price: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      base_price: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      tax_amount: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      discount_amount: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      price_incl_tax: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      tax_percent: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      item_img_url: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      item_brand_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      item_size: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      discount_amount: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      tax_amount: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      returnable: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      on_sale: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
        defaultValue: false
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_item'
    }
  );
  OrderItem.associate = function (models) {
    OrderItem.belongsTo(models.Order, { foreignKey: 'order_id' });
  };

  return OrderItem;
};
