module.exports = function (sequelize, DataTypes) {
  const SplitSubSalesOrderItem = sequelize.define(
    'SplitSubSalesOrderItem',
    {
      split_sub_item_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      sub_item_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: '0',
      },
      main_item_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
      },
      coupon_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      coupon_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      discount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      parent_order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      is_gift_voucher: {
        type: DataTypes.TINYINT,
        allowNull: true,
        defaultValue: '0',
      },
      gift_voucher_refunded_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_sub_sales_order_item',
    }
  );

  SplitSubSalesOrderItem.associate = function (models) {
    // Add associations here
  };

  return SplitSubSalesOrderItem;
};
