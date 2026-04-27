module.exports = function (sequelize, DataTypes) {
  const OrderPayment = sequelize.define(
    'OrderPayment',
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
      additional_information: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      method: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_payment',
      timestamps: false
    }
  );
  OrderPayment.associate = function (models) {
    OrderPayment.belongsTo(models.Order, {
      foreignKey: 'entity_id'
    });
  };

  return OrderPayment;
};
