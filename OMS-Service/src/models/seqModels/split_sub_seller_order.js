module.exports = function (sequelize, DataTypes) {
  const SplitSubSellerOrder = sequelize.define(
    'SplitSubSellerOrder',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      order_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
      },
      main_order_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
      },
      shipment_mode: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      has_global_shipment: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      payment_id: {
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
      shukran_pr_successful: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null,
      },
      extra_1: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null,
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_sub_seller_order',
    }
  );

  SplitSubSellerOrder.associate = function (models) {
    // Add associations here
  };

  return SplitSubSellerOrder;
};
