const { DataTypes } = require('sequelize');
const columns = {
  requested_at: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  withdrawn_at: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  completed_at: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  ttl: {
    type: DataTypes.DATE,
    allowNull: true,
    defaultValue: null
  },
  reason: {
    type: DataTypes.STRING,
    allowNull: true,
    defaultValue: null
  },
  email: {
    type: DataTypes.STRING,
    allowNull: true,
    defaultValue: null
  },
  customer_id: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  },
  marked_for_delete: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  }
};

module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'DeletedCustomers',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      ...columns
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'delete_customers',
      timestamps: false
    }
  );
};
