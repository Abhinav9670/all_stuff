module.exports = function (sequelize, DataTypes) {
  const SplitSalesOrderPayment = sequelize.define(
    'SplitSalesOrderPayment',
    {
      entity_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        primaryKey: true,
        autoIncrement: true,
      },
      parent_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
      },
      split_parent_id: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: '0',
      },
      base_shipping_captured: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_captured: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      amount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_amount_paid: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      amount_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_amount_authorized: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_amount_paid_online: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_amount_refunded_online: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_amount: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      amount_paid: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      amount_authorized: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_amount_ordered: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_shipping_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      shipping_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_amount_refunded: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      amount_ordered: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      base_amount_canceled: {
        type: DataTypes.DECIMAL(20, 4),
        allowNull: false,
        defaultValue: null,
      },
      quote_payment_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
      },
      additional_data: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_exp_month: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_ss_start_year: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      echeck_bank_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      method: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_debug_request_body: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_secure_verify: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      protection_eligibility: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_approval: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_last_4: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_status_description: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      echeck_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_debug_response_serialized: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_ss_start_month: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      echeck_account_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      last_trans_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_cid_status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_owner: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_type: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      po_number: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_exp_year: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      echeck_routing_number: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      account_status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      anet_trans_method: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_debug_response_body: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_ss_issue: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      echeck_account_name: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_avs_status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_number_enc: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      cc_trans_id: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      address_status: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      },
      additional_information: {
        type: DataTypes.TEXT,
        allowNull: false,
        defaultValue: null,
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'split_sales_order_payment',
      timestamps: false
    }
  );

  SplitSalesOrderPayment.associate = function (models) {
  };

  return SplitSalesOrderPayment;
};
