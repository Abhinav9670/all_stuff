module.exports = function (sequelize, DataTypes) {
  const SellerCommissionDetails = sequelize.define(
    'SellerCommissionDetails',
    {
      id: {
        type: DataTypes.BIGINT.UNSIGNED,
        allowNull: false,
        autoIncrement: true,
        primaryKey: true
      },
      seller_name: {
        type: DataTypes.STRING(255),
        allowNull: true
      },
      seller_id: {
        type: DataTypes.STRING(128),
        allowNull: false
      },
      l4_category: {
        type: DataTypes.STRING(255),
        allowNull: false
      },
      commission_value: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false
      },
      commission_details: {
        type: DataTypes.JSON,
        allowNull: true
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'seller_commission_details',
      timestamps: true,
      createdAt: 'created_at',
      updatedAt: 'updated_at',
      indexes: [
        {
          name: 'idx_seller_commission_seller',
          fields: ['seller_id']
        }

      ]
    }
  );

  return SellerCommissionDetails;
};

