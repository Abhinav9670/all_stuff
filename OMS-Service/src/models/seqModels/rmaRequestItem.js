/* eslint-disable max-lines-per-function */
module.exports = function (sequelize, DataTypes) {
  const RmaRequestItem = sequelize.define(
    'RmaRequestItem',
    {
      request_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      request_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      order_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      qty: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      actual_qty_returned: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      qc_failed_qty: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      request_qty: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      reason_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      condition_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      resolution_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      item_status: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_rma_request_item',
      timestamps: false
    }
  );

  RmaRequestItem.associate = function (models) {
    RmaRequestItem.belongsTo(models.RmaRequest, { foreignKey: 'request_id' });
  };

  return RmaRequestItem;
};
