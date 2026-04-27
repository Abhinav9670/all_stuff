module.exports = function (sequelize, DataTypes) {
  return sequelize.define(
    'StoreWebsite',
    {
      website_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: null,
        autoIncrement: true,
        primaryKey: true
      },
      code: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      name: {
        type: DataTypes.TEXT,
        allowNull: true,
        defaultValue: null
      },
      sort_order: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      default_group_id: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0
      },
      is_default: {
        type: DataTypes.INTEGER,
        allowNull: true,
        defaultValue: 0
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'store_website',
      timestamps: false
    }
  );
};
