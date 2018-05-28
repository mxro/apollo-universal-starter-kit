export async function up(knex) {
  return knex.schema.createTable('message', table => {
    table.increments();
    table.string('text').notNull();
    table.integer('userId');
    table.timestamps(false, true);
  });
}

export async function down(knex) {
  return knex.schema.dropTable('message');
}