module.exports = (sequelize, DataTypes) => {
  return sequelize.define(
    'OrderStatus',
    {
      status: {
        type: DataTypes.STRING,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      label: {
        type: DataTypes.STRING,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_status',
      timestamps: false
    }
  );
};
