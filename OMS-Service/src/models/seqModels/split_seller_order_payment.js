module.exports = function (sequelize, DataTypes) {
  const SplitSellerOrderPayment = sequelize.define(
    'SplitSellerOrderPayment',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      parent_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
      },
      main_order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      amount_paid: {
        type: DataTypes.DOUBLE,
        allowNull: false,
        defaultValue: null,
      },
      method: {
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
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_seller_order_payment',
    }
  );

  SplitSellerOrderPayment.associate = function (models) {
    // Add associations here
  };

  return SplitSellerOrderPayment;
};
