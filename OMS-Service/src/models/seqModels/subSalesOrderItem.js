module.exports = (sequelize, DataTypes) => {
  const SubSalesOrderItem = sequelize.define(
    'SubSalesOrderItem',
    {
      sub_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      main_item_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      coupon_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      coupon_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      discount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: 0.0
      },
      parent_order_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      is_gift_voucher: {
        type: DataTypes.BOOLEAN,
        allowNull: true,
        defaultValue: false
      },
      shukran_card_number: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      gift_voucher_refunded_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: 0.0
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sub_sales_order_item',
      timestamps: false
    }
  );

  return SubSalesOrderItem;
};
