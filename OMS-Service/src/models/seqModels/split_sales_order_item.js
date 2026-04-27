module.exports = function (sequelize, DataTypes) {
  const SplitSalesOrderItem = sequelize.define(
    'SplitSalesOrderItem',
    {
      item_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      order_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: '0',
      },
      split_order_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: '0',
      },
      seller_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shipment_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      seller_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      warehouse_location_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      parent_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      quote_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      store_id: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      created_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: sequelize.fn('NOW'),
      },
      updated_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: sequelize.fn('NOW'),
      },
      product_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      product_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      product_options: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      weight: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      is_virtual: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      sku: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      description: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      applied_rule_ids: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      additional_data: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      is_qty_decimal: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      no_discount: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: '0',
      },
      qty_backordered: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      qty_canceled: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      qty_invoiced: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      qty_ordered: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      qty_refunded: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      qty_shipped: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      base_cost: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      base_price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      original_price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_original_price: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_percent: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      tax_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      base_tax_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      tax_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      base_tax_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      discount_percent: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      discount_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      base_discount_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      discount_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      base_discount_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      amount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      base_amount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      row_total: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      base_row_total: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      row_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      base_row_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      row_weight: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      base_tax_before_discount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_before_discount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      ext_order_item_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      locked_do_invoice: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      locked_do_ship: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      price_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_price_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      row_total_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_row_total_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_tax_compensation_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_discount_tax_compensation_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_tax_compensation_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_discount_tax_compensation_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_tax_compensation_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_discount_tax_compensation_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_canceled: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_tax_compensation_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_tax_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_discount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      gift_message_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      gift_message_available: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      free_shipping: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: '0',
      },
      weee_tax_applied: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      weee_tax_applied_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      weee_tax_applied_row_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      weee_tax_disposition: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      weee_tax_row_disposition: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_weee_tax_applied_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_weee_tax_applied_row_amnt: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_weee_tax_disposition: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_weee_tax_row_disposition: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      original_base_price: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      parent_sku: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      seller_qty_cancelled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      returnable: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      hsn_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: '',
      },
      item_img_url: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      item_brand_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      item_size: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shukran_coins_earned: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      shukran_coins_burned: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      shukran_coins_burned_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      shukran_coins_burned_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      shukran_coins_earned_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      shukran_coins_earned_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      shukran_l4_category: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      on_sale: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      lmd_flag: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      sales_order_item_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      estimated_delivery_date: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      min_estimated_date: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      max_estimated_date: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      store_credit_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      payfort_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      bnpl_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_sales_order_item',
    }
  );

  SplitSalesOrderItem.associate = function (models) {
    // Add associations here
  };

  return SplitSalesOrderItem;
};
