/**
 * This Action was migrated from Rule.
 * Rule name: Force email verification
 * Rule ID: rul_
 * Created on 9/22/2024
 */

/**
 * Handler that will be called during the execution of a PostLogin flow.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
exports.onExecutePostLogin = async (event, api) => {

  console.log('Received user', event.user);
  console.debug('Received context', event);

  if (!event.user.email_verified) {

    const error = new Error(
      JSON.stringify({
        code: 'unauthorized',
        message: 'You have to confirm your email address before continuing.',
        statusCode: 401,
      })
    );

    console.log('email need to be verified.. denying: ' + JSON.stringify(error));
    return api.access.deny(error.message);
    //return api.access.deny(JSON.stringify(error));

  } else {
    return;
  }
}


/**
 * Handler that will be invoked when this action is resuming after an external redirect. If your
 * onExecutePostLogin function does not perform a redirect, this function can be safely ignored.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
// exports.onContinuePostLogin = async (event, api) => {
// };
