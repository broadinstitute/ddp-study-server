function(user, context, callback) {
  const adminRole = 'Basil Prism Admin';

  // Note that client metadata key/values are all strings.
  context.clientMetadata = context.clientMetadata || {};
  const isAdminClient = context.clientMetadata.isAdminClient === 'true';
  if (!isAdminClient) {
    console.log('Not an admin client, skipping check-admin-role rule');
    return callback(null, user, context);
  }

  const assignedRoles = (context.authorization || {}).roles;
  if (assignedRoles.includes(adminRole)) {
    console.log('Current user has admin role, continuing');
    return callback(null, user, context);
  }

  // Throw error so we halt the auth pipeline and skip the Pepper registration rule.
  console.log(`Did not find admin role for user with email ${user.email}`);
  return callback(new Error('Not an authorized admin'));
}
