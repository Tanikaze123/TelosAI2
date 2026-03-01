module.exports = (sequelize, DataTypes) => {
  const Replay = sequelize.define('Replay', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    filename: {
      type: DataTypes.STRING,
      allowNull: false
    },
    duration: {
      type: DataTypes.INTEGER,
      comment: 'Duration in ticks'
    },
    recordedAt: {
      type: DataTypes.DATE
    },
    mcVersion: {
      type: DataTypes.STRING(20),
      field: 'mc_version'
    },
    serverName: {
      type: DataTypes.STRING(100),
      field: 'server_name'
    },
    parsedData: {
      type: DataTypes.JSONB,
      field: 'parsed_data',
      comment: 'Full parsed timeline and entity data'
    },
    metadata: {
      type: DataTypes.JSONB,
      comment: 'Additional metadata from .mcpr file'
    },
    parseStatus: {
      type: DataTypes.ENUM('pending', 'parsing', 'completed', 'failed'),
      defaultValue: 'pending',
      field: 'parse_status'
    },
    parseError: {
      type: DataTypes.TEXT,
      field: 'parse_error'
    }
  }, {
    tableName: 'replays',
    timestamps: true,
    underscored: true,
    indexes: [
      {
        fields: ['recorded_at']
      },
      {
        fields: ['parse_status']
      },
      {
        using: 'GIN',
        fields: ['parsed_data']
      }
    ]
  });

  Replay.associate = (models) => {
    // Future: Add associations with observations, patterns, etc.
  };

  return Replay;
};
