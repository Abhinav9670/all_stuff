module.exports = function (sequelize, DataTypes) {
  const SellerConfig = sequelize.define(
    'SellerConfig',
    {
      id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        primaryKey: true,
        autoIncrement: true
      },
      SELLER_ID: {
        type: DataTypes.STRING(255),
        allowNull: false,
        comment: 'Seller identifier (can have multiple warehouses)',
        field: 'SELLER_ID'
      },
      styli_warehouse_id: {
        type: DataTypes.STRING(255),
        allowNull: false,
        comment: 'Styli Warehouse ID - Must be unique in combination with seller_warehouse_id'
      },
      seller_warehouse_id: {
        type: DataTypes.STRING(255),
        allowNull: false,
        comment: 'Seller Warehouse ID - Must be unique in combination with styli_warehouse_id'
      },
      seller_type: {
        type: DataTypes.STRING(100),
        allowNull: false,
        comment: 'apparel, unicommerce, seller_central_luna'
      },
      basic_settings: {
        type: DataTypes.JSON,
        allowNull: false,
        comment: 'Stores all basic settings fields'
      },
      configuration: {
        type: DataTypes.JSON,
        allowNull: false,
        comment: 'Stores all configuration fields'
      },
      address: {
        type: DataTypes.JSON,
        allowNull: false,
        comment: 'Stores all address fields'
      },
      created_by: {
        type: DataTypes.STRING(255),
        allowNull: true
      },
      updated_by: {
        type: DataTypes.STRING(255),
        allowNull: true
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'seller_config',
      timestamps: true,
      createdAt: 'created_at',
      updatedAt: 'updated_at',
      indexes: [
        {
          unique: true,
          name: 'unique_warehouse_combination',
          fields: ['styli_warehouse_id', 'seller_warehouse_id']
        },
        {
          name: 'idx_seller_id',
          fields: ['SELLER_ID']
        },
        {
          name: 'idx_seller_type',
          fields: ['seller_type']
        }
      ]
    }
  );

  return SellerConfig;
};
