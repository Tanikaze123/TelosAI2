module.exports = {
  up: async (queryInterface, Sequelize) => {
    await queryInterface.createTable('entities', {
      id: {
        type: Sequelize.UUID,
        defaultValue: Sequelize.UUIDV4,
        primaryKey: true
      },
      entity_type: {
        type: Sequelize.STRING(100),
        allowNull: false
      },
      model_id: {
        type: Sequelize.STRING(100)
      },
      composite_signature: {
        type: Sequelize.JSONB
      },
      display_name: {
        type: Sequelize.STRING(100)
      },
      confirmed: {
        type: Sequelize.BOOLEAN,
        defaultValue: false
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

    await queryInterface.addIndex('entities', ['entity_type']);
    await queryInterface.addIndex('entities', ['model_id']);
  },

  down: async (queryInterface, Sequelize) => {
    await queryInterface.dropTable('entities');
  }
};
