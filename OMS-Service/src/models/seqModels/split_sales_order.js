module.exports = function (sequelize, DataTypes) {
  const SplitSalesOrder = sequelize.define(
    'SplitSalesOrder',
    {
      entity_id: {
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
      shipment_mode: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      has_global_shipment: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      estimated_delivery: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      state: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      coupon_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      protect_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shipping_description: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      is_virtual: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      store_id: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      customer_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      base_discount_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_discount_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_discount_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_discount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_grand_total: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_tax_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_tax_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_subtotal: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_subtotal_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_subtotal_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_subtotal_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_tax_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_tax_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_tax_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_tax_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_to_global_rate: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_to_order_rate: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_invoiced_cost: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_offline_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_online_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_paid: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_qty_ordered: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      discount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      grand_total: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_tax_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_tax_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      store_to_base_rate: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      store_to_order_rate: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      subtotal: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      subtotal_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      subtotal_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      subtotal_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      tax_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_invoiced: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_offline_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_online_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_paid: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_qty_ordered: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      can_ship_partially: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      can_ship_partially_item: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      customer_is_guest: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      customer_note_notify: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      billing_address_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      customer_group_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      edit_increment: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      email_sent: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      send_email: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      forced_shipment_with_invoice: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      payment_auth_expiration: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      quote_address_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      quote_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      shipping_address_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      adjustment_negative: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      adjustment_positive: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_adjustment_negative: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_adjustment_positive: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_discount_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_subtotal_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_total_due: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      payment_authorization_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_discount_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      subtotal_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      total_due: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      weight: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      customer_dob: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      increment_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      applied_rule_ids: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      base_currency_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_email: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_firstname: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_lastname: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_middlename: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_prefix: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_suffix: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_taxvat: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      discount_description: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      ext_customer_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      ext_order_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      global_currency_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      hold_before_state: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      hold_before_status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      order_currency_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      original_increment_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      relation_child_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      relation_child_real_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      relation_parent_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      relation_parent_real_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      remote_ip: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shipping_method: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      store_currency_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      store_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      x_forwarded_for: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_note: {
        type: DataTypes.TEXT,
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
      total_item_count: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: '0',
      },
      customer_gender: {
        type: DataTypes.INTEGER,
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
      shipping_discount_tax_compensation_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_discount_tax_compensation_amnt: {
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
      shipping_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_incl_tax: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      coupon_rule_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      paypal_ipn_customer_notified: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: '0',
      },
      gift_message_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      original_shipping_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      base_original_shipping_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      cash_on_delivery_fee: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      base_cash_on_delivery_fee: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      wms_status: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      wms_pull_status: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      amstorecredit_base_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      amstorecredit_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      amstorecredit_invoiced_base_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      amstorecredit_invoiced_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      amstorecredit_refunded_base_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      amstorecredit_refunded_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      to_refund: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      source: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      merchant_reference: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      app_version: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      order_data_updated: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      coupon_source_external: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: '0',
      },
      cancellation_reason: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cancellation_reason_id: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      delivered_at: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      estimated_delivery_time: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      clickpost_message: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      import_fee: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_import_fee: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      retry_payment: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      uuid: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      gift_voucher_discount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: '0.0000',
      },
      payfort_authorized: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      authorization_capture: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_sales_order',
    }
  );

  SplitSalesOrder.associate = function (models) {
    // Associations for split sales orders
    SplitSalesOrder.hasMany(models.Shipment, {
      foreignKey: 'order_id',
      sourceKey: 'order_id',
      as: 'Shipments',
      rejectOnEmpty: false
    });

    SplitSalesOrder.hasMany(models.OrderAddress, {
      foreignKey: 'parent_id',
      sourceKey: 'order_id',
      as: 'OrderAddresses',
      rejectOnEmpty: false
    });

    SplitSalesOrder.hasMany(models.SplitSalesOrderItem, {
      foreignKey: 'split_order_id',
      sourceKey: 'entity_id',
      as: 'SplitSalesOrderItems',
      rejectOnEmpty: false
    });

    SplitSalesOrder.hasMany(models.SplitSalesOrderPayment, {
      foreignKey: 'split_parent_id',
      sourceKey: 'entity_id',
      as: 'SplitSalesOrderPayments',
      rejectOnEmpty: false
    });

    SplitSalesOrder.hasMany(models.Creditmemo, {
      foreignKey: 'order_id',
      sourceKey: 'order_id',
      as: 'Creditmemos',
      rejectOnEmpty: false
    });

    SplitSalesOrder.hasMany(models.SplitSubSalesOrder, {
      foreignKey: 'split_order_id',
      sourceKey: 'entity_id',
      as: 'SplitSubSalesOrders',
      rejectOnEmpty: false
    });
  };

  return SplitSalesOrder;
};
