module.exports = (sequelize, DataTypes) => {
  return sequelize.define(
    'OrderStatusState',
    {
      status: {
        type: DataTypes.STRING,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      state: {
        type: DataTypes.STRING,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      is_default: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      visible_on_front: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      step: {
        type: DataTypes.INTEGER,
        allowNull: false
      },
      color_state: {
        type: DataTypes.INTEGER,
        allowNull: false
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_status_state',
      timestamps: false
    }
  );
};
