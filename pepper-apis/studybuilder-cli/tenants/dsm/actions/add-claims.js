/**
 * This Action was migrated from Rule.
 * Rule name: Add claims
 * Rule ID: rul_
 * Created on 9/16/2024
 */

/**
 * Handler that will be called during the execution of a PostLogin flow.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
exports.onExecutePostLogin = async (event, api) => {
  /**
   * The following code block will skip this Action if Rule "Add claims"
   * was previously executed in the transaction in order to avoid duplication
   * of logic.
   */
  if (api.rules.wasExecuted("rul_qE6QN5GnYTDhtbmg")) {
    return;
  }

  // YOUR_CODE_HERE
  console.log('Executing DSM Add Claims action');
  var pepperClientClaim = 'https://datadonationplatform.org/cid';
  var pepperTenantClaim = 'https://datadonationplatform.org/t';
  var auth0UserIdClaim = 'https://datadonationplatform.org/auth0Uid';

  var tenantDomain = 'https://' + event.secrets.domain + '/';
  api.idToken.setCustomClaim(pepperClientClaim, event.client.client_id);
  api.idToken.setCustomClaim(pepperTenantClaim, tenantDomain);
  api.idToken.setCustomClaim(auth0UserIdClaim, event.user.userId);

  return;
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
