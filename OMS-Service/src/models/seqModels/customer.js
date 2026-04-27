module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'Customer',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      phone_number: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'new_customer_entity'
    }
  );
};
