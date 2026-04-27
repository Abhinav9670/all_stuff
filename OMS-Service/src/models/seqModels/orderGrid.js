module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'OrderGrid',
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
      },
      customer_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      store_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_grid'
    }
  );
};
