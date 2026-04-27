const { DataTypes } = require('sequelize');

module.exports = function (sequelize, DataTypes) {
  const SubSalesOrder = sequelize.define(
    'SubSalesOrder',
    {
      id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        primaryKey: true,
        autoIncrement: true
      },
      order_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null
      },
      external_coupon_redemption_tracking_id: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      external_coupon_redemption_status: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      external_auto_coupon_code: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      external_auto_coupon_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      external_auto_coupon_base_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      query_locked: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      client_platform: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      dtf_locked: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      external_quote_id: {
        type: DataTypes.BIGINT,
        allowNull: true,
        defaultValue: null
      },
      review_required: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      external_quote_status: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      promo_offers: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      faster_delivery: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      base_donation_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      donation_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      whitelisted_customer: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      is_stylipost: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      shipping_label: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      client_source: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      warehouse_id: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      is_unfulfillment_order: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      is_otp_verified: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      extra_1: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      extra_2: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      payment_id: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      free_shipping_type_order: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      eas_coins: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      eas_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      eas_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      device_id: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      initial_eas_coins: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      initial_eas_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      initial_eas_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      retry_payment: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      order_expired_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: null
      },
      payment_pending_first_notification_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: null
      },
      payment_pending_second_notification_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: null
      },
      retry_payment_count: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      retry_payment_count_threshold: {
        type: DataTypes.SMALLINT,
        allowNull: true,
        defaultValue: null
      },
      gift_voucher_refunded_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      total_shukran_coins_earned: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      total_shukran_coins_burned: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      total_shukran_burned_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      total_shukran_burned_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      total_shukran_earned_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      total_shukran_earned_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      quote_id: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      customer_profile_id: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      shukran_locked: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      shukran_tenders: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      shukran_store_code: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      total_shukran_returned: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      shukran_linked: {
        type: DataTypes.TINYINT,
        allowNull: true,
        defaultValue: null
      },
      qualified_purchase: {
        type: DataTypes.TINYINT,
        allowNull: true,
        defaultValue: null
      },
      cross_border: {
        type: DataTypes.TINYINT,
        allowNull: true,
        defaultValue: null
      },
      tier_name: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      shukran_pr_successful: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: null
      },
      shukran_pr_transaction_net_total: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      pr_updated_at: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: null
      },
      shukran_phone_number: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      shukran_card_number: {
        type: DataTypes.STRING,
        allowNull: true,
        defaultValue: null
      },
      shukran_bonus_earn_point: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      },
      shukran_basic_earn_point: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: true,
        defaultValue: null
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sub_sales_order',
      timestamps: false
    }
  );

  SubSalesOrder.associate = function (models) {
    SubSalesOrder.belongsTo(models.Order, {
      foreignKey: 'order_id',
      as: 'order'
    });
    SubSalesOrder.hasMany(models.SubSalesOrderItem, {
      foreignKey: 'sub_item_id',
      as: 'items'
    });
  };

  return SubSalesOrder;
};
