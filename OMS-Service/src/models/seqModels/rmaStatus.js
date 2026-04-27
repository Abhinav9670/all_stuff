module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'RmaStatus',
    {
      status_id: {
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
      status_code: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      state: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      color: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      is_enabled: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 1
      },
      priority: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_rma_status',
      timestamps: false
    }
  );

  // RmaStatus.associate = function (models) {
  //   RmaStatus.belongsTo(models.RmaRequest, {
  //     foreignKey: 'status'
  //     // targetKey: 'status_id'
  //   });
  // };
};
