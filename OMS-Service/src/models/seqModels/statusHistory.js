module.exports = (sequelize, DataTypes) => {
  const DEFAULT_TIME = '0000-00-00 00:00:00';
  return sequelize.define(
    'StatusHistory',
    {
      id: {
        allowNull: false,
        autoIncrement: true,
        primaryKey: true,
        type: DataTypes.INTEGER
      },
      order_id: {
        type: DataTypes.STRING,
        allowNull: false
      },
      split_order_id: {
        type: DataTypes.STRING,
        allowNull: true
      },
      split_order_increment_id: {
        type: DataTypes.STRING,
        allowNull: true
      },
      processing_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      cancel_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      packed_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      shipped_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      rto_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      delivered_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      picked_up_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      refunded_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      },
      received_warehouse_date: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: DEFAULT_TIME
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'status_change_history'
    }
  );
};
