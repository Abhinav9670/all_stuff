module.exports = (sequelize, DataTypes) => {
  return sequelize.define(
    'OrderComment',
    {
      entity_id: {
        allowNull: false,
        autoIncrement: true,
        primaryKey: true,
        type: DataTypes.INTEGER
      },
      parent_id: {
        type: DataTypes.STRING,
        allowNull: false
      },
      is_visible_on_front: {
        type: 'TIMESTAMP',
        allowNull: true,
        defaultValue: '0'
      },
      comment: {
        type: DataTypes.STRING,
        allowNull: true
      },
      status: {
        type: DataTypes.STRING,
        allowNull: true
      },
      final_status: {
        type: DataTypes.STRING,
        allowNull: true
      },
      created_at: {
        type: 'TIMESTAMP',
        defaultValue: sequelize.fn('NOW')
      },
      entity_name: {
        type: DataTypes.STRING,
        allowNull: false
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_order_status_history',
      timestamps: false
    }
  );
};
