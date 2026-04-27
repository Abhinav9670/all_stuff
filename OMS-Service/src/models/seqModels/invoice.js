module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'Invoice',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      increment_id: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      created_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_invoice',
      timestamps: false
    }
  );
};
