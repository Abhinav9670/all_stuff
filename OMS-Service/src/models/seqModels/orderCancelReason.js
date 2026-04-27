module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'OrderCancelReason',
    {
      reason_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      title: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      status: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      sort_order: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_cancel_reason',
      timestamps: false
    }
  );
};
