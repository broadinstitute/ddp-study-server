/**
 * This Action was migrated from Rule.
 * Rule name: Force email verification
 * Created on 9/24/2024
 */

/**
 * Handler that will be called during the execution of a PostLogin flow.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
exports.onExecutePostLogin = async (event, api) => {
  /**
   * The following code block will skip this Action if Rule "Force email verification"
   * was previously executed in the transaction in order to avoid duplication
   * of logic.
   */
  if (api.rules.wasExecuted("rul_bL3jV04z2KADpdx9")) {
    return;
  }

  // YOUR_CODE_HERE
  console.log('Received user', event.user);
  console.log('Received context', event);

  const auth0Sdk = require("auth0");
  if (!event.user.email_verified) {
    const ManagementClient = auth0Sdk.ManagementClient;
    // This will make an Authentication API call
    const managementClientInstance = new ManagementClient({
      // These come from a machine-to-machine application
      domain: event.secrets.M2M_DOMAIN,
      clientId: event.secrets.M2M_CLIENT_ID,
      clientSecret: event.secrets.M2M_CLIENT_SECRET,
      scope: "update:users"
    });

    const params = {
      user_id: event.user.user_id,
      client_id: event.client.client_id,
    };

    console.log('Attempt to resend a confirmation email');

    managementClientInstance.jobs.verifyEmail(params, function (err) {
      if (err) {
        console.log(
          'Request to resend a confirmation email failed',
          'The error is',
          err
        );
      } else {
        console.log(
          'Successfully created a job to resend a confirmation email'
        );
      }

      const error = new Error(
        JSON.stringify({
          code: 'unauthorized',
          message: 'You have to confirm your email address before continuing.',
          statusCode: 401,
        })
      );

      return api.access.deny(error.message);
    });
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
