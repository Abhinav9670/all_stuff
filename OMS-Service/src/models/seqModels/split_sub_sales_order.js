module.exports = function (sequelize, DataTypes) {
  const SplitSubSalesOrder = sequelize.define(
    'SplitSubSalesOrder',
    {
      id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      order_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
      },
      split_order_id: {
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
      external_coupon_redemption_tracking_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      external_coupon_redemption_status: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      external_auto_coupon_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      external_auto_coupon_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      external_auto_coupon_base_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: null,
      },
      query_locked: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      client_platform: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      dtf_locked: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      external_quote_id: {
        type: DataTypes.BIGINT,
        allowNull: false,
        defaultValue: null,
      },
      review_required: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      external_quote_status: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      promo_offers: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      faster_delivery: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      base_donation_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      donation_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      whitelisted_customer: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      is_stylipost: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      shipping_label: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      client_source: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      warehouse_id: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      is_unfulfillment_order: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: null,
      },
      is_otp_verified: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      extra_1: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      extra_2: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      payment_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      free_shipping_type_order: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      eas_coins: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: '0',
      },
      eas_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      eas_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      device_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      initial_eas_coins: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: '0',
      },
      initial_eas_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      initial_eas_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      retry_payment: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      order_expired_at: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      payment_pending_first_notification_at: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      payment_pending_second_notification_at: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      retry_payment_count: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      retry_payment_count_threshold: {
        type: DataTypes.SMALLINT,
        allowNull: false,
        defaultValue: '0',
      },
      gift_voucher_refunded_amount: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      total_shukran_coins_earned: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      total_shukran_coins_burned: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      total_shukran_burned_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      total_shukran_burned_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      total_shukran_earned_value_in_base_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      total_shukran_earned_value_in_currency: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      quote_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      customer_profile_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shukran_locked: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      shukran_tenders: {
        type: DataTypes.JSON,
        allowNull: false,
        defaultValue: null,
      },
      shukran_store_code: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      total_shukran_returned: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: '0',
      },
      shukran_linked: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      qualified_purchase: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      cross_border: {
        type: DataTypes.TINYINT,
        allowNull: false,
        defaultValue: '0',
      },
      tier_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shukran_pr_successful: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      shukran_pr_transaction_net_total: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      pr_updated_at: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: null,
      },
      shukran_phone_number: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shukran_card_number: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      shukran_bonus_earn_point: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      },
      shukran_basic_earn_point: {
        type: DataTypes.DECIMAL(12, 4),
        allowNull: false,
        defaultValue: '0.0000',
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_sub_sales_order',
    }
  );

  SplitSubSalesOrder.associate = function (models) {
    // Add associations here
  };

  return SplitSubSalesOrder;
};
