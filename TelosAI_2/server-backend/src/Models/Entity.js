module.exports = (sequelize, DataTypes) => {
  const Entity = sequelize.define('Entity', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    entityType: {
      type: DataTypes.STRING(100),
      allowNull: false,
      field: 'entity_type'
    },
    modelId: {
      type: DataTypes.STRING(100),
      field: 'model_id',
      comment: 'ModelEngine model identifier'
    },
    compositeSignature: {
      type: DataTypes.JSONB,
      field: 'composite_signature',
      comment: 'Signature for multi-entity bosses'
    },
    displayName: {
      type: DataTypes.STRING(100),
      field: 'display_name',
      comment: 'User-friendly name'
    },
    confirmed: {
      type: DataTypes.BOOLEAN,
      defaultValue: false,
      comment: 'Confirmed by user via GUI'
    }
  }, {
    tableName: 'entities',
    timestamps: true,
    underscored: true,
    indexes: [
      {
        fields: ['entity_type']
      },
      {
        fields: ['model_id']
      }
    ]
  });

  Entity.associate = (models) => {
    // Future: Add associations
  };

  return Entity;
};
