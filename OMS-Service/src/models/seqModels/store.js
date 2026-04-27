const { DataTypes } = require('sequelize');
const columns = {
  sort_order: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 0
  },
  group_id: {
    type: DataTypes.INTEGER,
    allowNull: false,
    defaultValue: 0
  },
  website_id: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: 0
  },
  is_active: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: 1
  },
  is_external: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: 0
  },
  warehouse_location_code: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  warehouse_inventory_table: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  currency: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  currency_conversion_rate: {
    type: DataTypes.DECIMAL,
    allowNull: true,
    defaultValue: null
  }
};
module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'Store',
    {
      store_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        autoIncrement: true,
        primaryKey: true
      },
      code: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      name: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      ...columns
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'store',
      timestamps: false
    }
  );
};
