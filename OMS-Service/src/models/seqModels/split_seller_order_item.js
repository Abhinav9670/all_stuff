module.exports = function (sequelize, DataTypes) {
  const SplitSellerOrderItem = sequelize.define(
    'SplitSellerOrderItem',
    {
      item_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      sales_order_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      main_order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      store_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      parent_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      sku: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      seller_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      seller_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      warehouse_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shipment_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      created_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: sequelize.fn('NOW'),
      },
      updated_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: sequelize.fn('NOW'),
      },
      seller_order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      estimated_delivery_date: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      min_estimated_date: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      max_estimated_date: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      qty_shipped: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: 0,
      },
      qty_ordered: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: 0,
      },
      product_type: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null,
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_seller_order_item',
    }
  );

  SplitSellerOrderItem.associate = function (models) {
    // Add associations here
  };

  return SplitSellerOrderItem;
};
