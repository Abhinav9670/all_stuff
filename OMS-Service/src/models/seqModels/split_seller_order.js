module.exports = function (sequelize, DataTypes) {
  const SplitSellerOrder = sequelize.define(
    'SplitSellerOrder',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      main_order_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: '0',
      },
      split_order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      seller_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      warehouse_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shipment_mode: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      increment_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      has_global_shipment: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      estimate_delivery: {
        type: DataTypes.DATE,
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
      status: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null,
      },
      // FBS (Fulfilled by Styli) - identifies the actual owner seller when seller_id is '0001'
      owner_seller_id: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null,
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_seller_order',
    }
  );

  SplitSellerOrder.associate = function (models) {
    // Add associations here
  };

  return SplitSellerOrder;
};
