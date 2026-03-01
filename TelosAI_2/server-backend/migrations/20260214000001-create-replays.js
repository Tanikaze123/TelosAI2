module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable('replays', {
      id: {
        type: Sequelize.UUID,
        defaultValue: Sequelize.UUIDV4,
        primaryKey: true
      },
      filename: {
        type: Sequelize.STRING,
        allowNull: false
      },
      duration: {
        type: Sequelize.INTEGER
      },
      recorded_at: {
        type: Sequelize.DATE
      },
      mc_version: {
        type: Sequelize.STRING(20)
      },
      server_name: {
        type: Sequelize.STRING(100)
      },
      parsed_data: {
        type: Sequelize.JSONB
      },
      metadata: {
        type: Sequelize.JSONB
      },
      parse_status: {
        type: Sequelize.ENUM('pending', 'parsing', 'completed', 'failed'),
        defaultValue: 'pending'
      },
      parse_error: {
        type: Sequelize.TEXT
      },
      created_at: {
        type: Sequelize.DATE,
        allowNull: false
      },
      updated_at: {
        type: Sequelize.DATE,
        allowNull: false
      }
    });

    await queryInterface.addIndex('replays', ['recorded_at']);
    await queryInterface.addIndex('replays', ['parse_status']);
  },

  down: async (queryInterface, Sequelize) => {
    await queryInterface.dropTable('replays');
  }
};
