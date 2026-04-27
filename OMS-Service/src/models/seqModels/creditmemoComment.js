module.exports = (sequelize, DataTypes) => {
  const CreditmemoComment = sequelize.define(
    'CreditmemoComment',
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
      comment: {
        type: DataTypes.STRING,
        allowNull: true
      },
      created_at: {
        type: 'TIMESTAMP',
        defaultValue: sequelize.fn('NOW')
      }
    },
    {
      underscored: true,
      freezeTableName: true,
      tableName: 'sales_creditmemo_comment',
      timestamps: false
    }
  );

  CreditmemoComment.associate = function (models) {
    CreditmemoComment.belongsTo(models.Creditmemo, { foreignKey: 'parent_id' });
  };

  return CreditmemoComment;
};
