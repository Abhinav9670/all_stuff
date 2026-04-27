'use strict';
module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable(
      'status_change_history',
      {
        id: {
          allowNull: false,
          autoIncrement: true,
          primaryKey: true,
          type: Sequelize.INTEGER
        },
        order_id: {
          type: Sequelize.STRING,
          allowNull: false
        },
        split_order_id: {
          type: Sequelize.STRING,
          allowNull: true
        },
        split_order_increment_id: {
          type: Sequelize.STRING,
          allowNull: true
        },
        processing_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        pending_payment_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        cancel_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        packed_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        shipped_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        rto_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        delivered_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        picked_up_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        refunded_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        received_warehouse_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        rma_cancel_date: {
          type: 'TIMESTAMP',
          allowNull: true,
          defaultValue: '0000-00-00 00:00:00'
        },
        created_at: {
          allowNull: false,
          type: Sequelize.DATE,
          defaultValue: Sequelize.fn('NOW')
        },
        updated_at: {
          allowNull: false,
          type: Sequelize.DATE,
          defaultValue: Sequelize.fn('NOW')
        }
      },
      {
        underscored: true
      }
    );
  },
  down: async queryInterface => {
    await queryInterface.dropTable('status_change_history');
  }
};
