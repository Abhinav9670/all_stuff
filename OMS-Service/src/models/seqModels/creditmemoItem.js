module.exports = (sequelize, DataTypes) => {
  const columns = {
    tax_amount: {
      type: DataTypes.DECIMAL(10, 4),
      allowNull: false,
      defaultValue: null
    },
    row_total_incl_tax: {
      type: DataTypes.DECIMAL(10, 4),
      allowNull: false,
      defaultValue: null
    },
    order_item_id: {
      type: DataTypes.INTEGER,
      allowNull: false,
      defaultValue: null
    },
    voucher_amount: {
      type: DataTypes.DECIMAL(10, 4),
      allowNull: false,
      defaultValue: null
    }
  };

  const CreditmemoItem = sequelize.define(
    'CreditmemoItem',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true
      },
      parent_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      sku: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null
      },
      // Magento DECIMAL; INTEGER caused 0 / wrong reads (breaks qty derivation from amounts)
      price_incl_tax: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null
      },
      price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      base_price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      row_total: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      base_row_total: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      base_row_total_incl_tax: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      base_price_incl_tax: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      // Magento stores qty as DECIMAL; INTEGER caused wrong values when reading e.g. 1.000
      qty: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null
      },
      discount_amount: {
        type: DataTypes.DECIMAL(10, 4),
        allowNull: false,
        defaultValue: null
      },
      ...columns
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_creditmemo_item',
      timestamps: false
    }
  );
  CreditmemoItem.associate = function (models) {
    CreditmemoItem.hasMany(models.CreditmemoItemTax, {
      foreignKey: 'sales_creditmemo_item_id',
      rejectOnEmpty: false
    });
  };
  return CreditmemoItem;
};
