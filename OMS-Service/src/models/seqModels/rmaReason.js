module.exports = function (sequelize, DataTypes) {
  const RmaReason = sequelize.define(
    'RmaReason',
    {
      reason_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      title: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      payer: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      status: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      position: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      is_deleted: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_rma_reason',
      timestamps: false
    }
  );

  RmaReason.associate = function (models) {
    RmaReason.belongsTo(models.RmaCancelReason, { foreignKey: 'reason_id' });
  };

  return RmaReason;
};
