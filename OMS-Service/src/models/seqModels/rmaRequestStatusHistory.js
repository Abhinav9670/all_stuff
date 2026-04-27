module.exports = function (sequelize, DataTypes) {
  const RmaRequestStatusHistory = sequelize.define(
    'RmaRequestStatusHistory',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        autoIncrement: true,
        primaryKey: true
      },
      request_id: {
        type: DataTypes.INTEGER,
        allowNull: false
      },
      reference_number: {
        type: DataTypes.STRING,
        allowNull: true,
        comment: 'rma_inc_id from additional.latest_status.reference_number'
      },
      status: {
        type: DataTypes.STRING,
        allowNull: true,
        comment: 'from additional.latest_status.status'
      },
      created_at: {
        type: DataTypes.DATE,
        allowNull: true,
        comment: 'from additional.latest_status.timestamp'
      },
      notification_event_id: {
        type: DataTypes.STRING,
        allowNull: true,
        comment: 'from additional.notification_event_id'
      },
      waybill: {
        type: DataTypes.STRING,
        allowNull: true,
        comment: 'waybill from request body'
      },
      status_message: {
        type: DataTypes.STRING,
        allowNull: true,
        comment: 'user-friendly message for status (e.g. Your return item has been picked up)'
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_rma_request_status_history',
      timestamps: false
    }
  );

  RmaRequestStatusHistory.associate = function (models) {
    RmaRequestStatusHistory.belongsTo(models.RmaRequest, { foreignKey: 'request_id' });
  };

  return RmaRequestStatusHistory;
};
