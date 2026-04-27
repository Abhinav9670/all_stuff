module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'RmaCancelReason',
    {
      reason_store_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      reason_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      store_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      label: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_cancel_reason_store',
      timestamps: false
    }
  );

  // RmaStatus.associate = function (models) {
  //   RmaStatus.belongsTo(models.RmaRequest, {
  //     foreignKey: 'status'
  //     // targetKey: 'status_id'
  //   });
  // };
};
