module.exports = function (sequelize, DataTypes) {
  const SellerAsn = sequelize.define(
    'SellerAsn',
    {
      id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        primaryKey: true,
        autoIncrement: true,
      },
      status: {
        type: DataTypes.STRING,
        allowNull: true,
      },
      startTime: {
        type: DataTypes.DATE,
        allowNull: true,
      },
      endTime: {
        type: DataTypes.DATE,
        allowNull: true,
      },
      wms_status: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0,
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
      },
      asn_number: {
        type: DataTypes.STRING(255),
        allowNull: true,
      },
      seller_id: {
        type: DataTypes.STRING(50),
        allowNull: true,
        comment: 'Seller ID for seller-specific ASN tracking (new on-boarding sellers)'
      }
    },
    {
      tableName: 'seller_asn',
      timestamps: true,
      createdAt: 'created_at',
      updatedAt: 'updated_at',
    }
  );

  SellerAsn.associate = function (models) {
    // Define associations here
    SellerAsn.hasMany(models.SellerAsnDetails, {
      foreignKey: 'seller_asn_id',
      as: 'SellerAsnDetails'
    });
  };

  return SellerAsn;
};
