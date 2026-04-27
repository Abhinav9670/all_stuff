const { DataTypes } = require('sequelize');
const columns = {
  address_type: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  area: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  city: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  company: {
    type: DataTypes.TEXT,
    allowNull: false,
    defaultValue: null
  },
  country_id: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  },
  customer_address_id: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  },
  email: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  firstname: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  lastname: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  middlename: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  region: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  region_id: {
    type: DataTypes.INTEGER,
    allowNull: true,
    defaultValue: null
  },
  street: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  suffix: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  telephone: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  nearest_landmark: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  },
  postcode: {
    type: DataTypes.TEXT,
    allowNull: true,
    defaultValue: null
  }
};
module.exports = function (sequelize, DataTypes) {
  const OrderAddress = sequelize.define(
    'OrderAddress',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      parent_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      ...columns
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_address',
      timestamps: false
    }
  );
  OrderAddress.associate = function (models) {
    OrderAddress.belongsTo(models.Order, {
      foreignKey: 'entity_id'
    });
  };

  return OrderAddress;
};
