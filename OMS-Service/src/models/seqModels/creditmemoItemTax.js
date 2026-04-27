module.exports = (sequelize, DataTypes) => {
  const CreditmemoItemTax = sequelize.define(
    'CreditmemoItemTax',
    {
      sales_creditmemo_item_tax_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      sales_creditmemo_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },

      tax_country: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      tax_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      tax_percentage: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      tax_amount: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_creditmemo_item_tax',
      timestamps: false
    }
  );

  CreditmemoItemTax.associate = function (models) {
    CreditmemoItemTax.belongsTo(models.CreditmemoItem, {
      foreignKey: 'sales_creditmemo_item_id'
    });
  };

  return CreditmemoItemTax;
};
