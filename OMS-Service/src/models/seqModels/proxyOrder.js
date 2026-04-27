module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'ProxyOrder',
    {
      id: {
        type: DataTypes.BIGINT,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      quote_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      payment_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      quote: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      sales_order: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      increment_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      created_at: {
        type: 'TIMESTAMP',
        defaultValue: sequelize.fn('NOW')
      },
      updated_at: {
        type: 'TIMESTAMP',
        defaultValue: sequelize.fn('NOW')
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'proxy_order',
      timestamps: false
    }
  );
};
