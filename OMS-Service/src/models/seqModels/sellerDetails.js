module.exports = function (sequelize, DataTypes) {
  const SellerAsnDetails = sequelize.define(
    'SellerAsnDetails',
    {
      id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        primaryKey: true,
        autoIncrement: true,
      },
      seller_id: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      order_id: {
        type: DataTypes.INTEGER.UNSIGNED,
        allowNull: true,
      },
      split_order_id: {
        type: DataTypes.INTEGER.UNSIGNED,
        allowNull: true,
      },
      seller_name: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      shipment_type: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      warehouse_location_id: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      store_id: {
        type: DataTypes.SMALLINT.UNSIGNED,
        allowNull: true,
      },
      product_id: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      product_type: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      sku: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      name: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      description: {
        type: DataTypes.TEXT,
        allowNull: true,
      },
      item_size: {
        type: DataTypes.STRING(45),
        allowNull: true,
      },
      item_brand_name: {
        type: DataTypes.STRING(100),
        allowNull: true,
      },
      item_img_url: {
        type: DataTypes.TEXT,
        allowNull: true,
      },
      hsn_code: {
        type: DataTypes.STRING(15),
        allowNull: true,
      },
      weight: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      qty_ordered: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
      },
      qty_shipped: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
      },
      qty_invoiced: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
      },
      price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
      },
      base_price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
      },
      row_total: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
      },
      base_row_total: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
      },
      tax_percent: {
        type: DataTypes.DECIMAL(5, 2),
        allowNull: true,
        defaultValue: '0.00',
      },
      tax_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      waybill: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      increment_id: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      seller_asn_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        references: {
          model: 'seller_asn',
          key: 'id'
        }
      },
      created_at: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: DataTypes.NOW,
      },
      updated_at: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: DataTypes.NOW,
      },
      is_seller_central: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0,
      }
    },
    {
      tableName: 'seller_asn_details',
      timestamps: true,
      createdAt: 'created_at',
      updatedAt: 'updated_at',
    }
  );

  SellerAsnDetails.associate = function (models) {
    // Define associations here
    SellerAsnDetails.belongsTo(models.SellerAsn, {
      foreignKey: 'seller_asn_id',
      as: 'SellerAsn'
    });
  };

  return SellerAsnDetails;
};
