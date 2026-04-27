module.exports = (sequelize, DataTypes) => {
  const Shipment = sequelize.define(
    'Shipment',
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
      title: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      carrier_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      track_number: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_shipment_track'
    }
  );
  Shipment.associate = function (models) {
    Shipment.belongsTo(models.Order, { foreignKey: 'entity_id' });
  };

  return Shipment;
};
