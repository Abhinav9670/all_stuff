module.exports = function (sequelize, DataTypes) {
  const SplitSubSellerOrderItem = sequelize.define(
    'SplitSubSellerOrderItem',
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
      seller_order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_sub_seller_order_item',
    }
  );

  SplitSubSellerOrderItem.associate = function (models) {
    // Add associations here
  };

  return SplitSubSellerOrderItem;
};
