module.exports = (sequelize, DataTypes) => {
  const DEFAULT_TIME = '0000-00-00 00:00:00';
  return sequelize.define(
    'AmastyStoreCreditHistory',
    {
      history_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null,
        primaryKey: true
      },
      customer_history_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      customer_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      is_deduct: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      difference: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null
      },
      store_credit_balance: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null
      },
      action: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      action_data: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      message: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      created_at: {
        type: 'TIMESTAMP',
        allowNull: false,
        defaultValue: DEFAULT_TIME
      },
      store_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'amasty_store_credit_history',
      timestamps: false
    }
  );
};
