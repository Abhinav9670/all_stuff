module.exports = (sequelize, DataTypes) => {
  return sequelize.define(
    'AmastyStoreCredit',
    {
      store_credit_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      customer_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      store_credit: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null
      },
      returnable_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_store_credit',
      timestamps: false
    }
  );
};
