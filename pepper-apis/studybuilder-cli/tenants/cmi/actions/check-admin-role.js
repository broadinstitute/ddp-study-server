/**
 * This Action was migrated from Rule.
 * Rule name: Check Admin Role
 * Rule ID: rul_
 * Created on 9/24/2024
 */

/**
 * Handler that will be called during the execution of a PostLogin flow.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
exports.onExecutePostLogin = async (event, api) => {
  const adminRole = 'CMI Prism Admin';
  console.log('Executing check-admin-role action');

  const isAdminClient = event.client.metadata.isAdminClient === 'true';
  if (!isAdminClient) {
    console.log('Not an admin client, skipping check-admin-role rule');
    return;
  }

  const assignedRoles = (event.authorization || {}).roles || [];
  if (assignedRoles.includes(adminRole)) {
    console.log('Current user has admin role, continuing');
    return;
  }

  // Throw error so we halt the auth pipeline and skip the Pepper registration rule.
  console.log(`Did not find admin role for user with email ${event.user.email}`);
  return api.access.deny('Not an authorized admin');

};


/**
 * Handler that will be invoked when this action is resuming after an external redirect. If your
 * onExecutePostLogin function does not perform a redirect, this function can be safely ignored.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
// exports.onContinuePostLogin = async (event, api) => {
// };
