module.exports = function (sequelize, DataTypes) {
  const RmaTracking = sequelize.define(
    'RmaTracking',
    {
      tracking_id: {
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
      tracking_code: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      tracking_number: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_rma_tracking',
      timestamps: false
    }
  );

  RmaTracking.associate = function (models) {
    RmaTracking.belongsTo(models.RmaRequest, { foreignKey: 'request_id' });
  };

  return RmaTracking;
};
