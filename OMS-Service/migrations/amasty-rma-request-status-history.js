'use strict';

module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable(
      'amasty_rma_request_status_history',
      {
        entity_id: {
          allowNull: false,
          autoIncrement: true,
          primaryKey: true,
          type: Sequelize.INTEGER
        },
        request_id: {
          type: Sequelize.INTEGER,
          allowNull: false
        },
        reference_number: {
          type: Sequelize.STRING,
          allowNull: true
        },
        status: {
          type: Sequelize.STRING,
          allowNull: true
        },
        created_at: {
          type: Sequelize.DATE,
          allowNull: true
        },
        notification_event_id: {
          type: Sequelize.STRING,
          allowNull: true
        },
        waybill: {
          type: Sequelize.STRING,
          allowNull: true
        }
      },
      {
        underscored: true
      }
    );
  },
  down: async (queryInterface) => {
    await queryInterface.dropTable('amasty_rma_request_status_history');
  }
};
