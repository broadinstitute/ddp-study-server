/**
 * This Action was migrated from Rule.
 * Rule name: Google Sign In Restriction
 * Rule ID: rul_nSlTGpLpwQlfA278
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
   * The following code block will skip this Action if Rule "Google Sign In Restriction"
   * was previously executed in the transaction in order to avoid duplication
   * of logic.
   */
  const GOOGLE_CONNECTION_IDENTIFIER = 'google-oauth2';

  const app_metadata = event.user.app_metadata || {};
  const pepperUserGuid = event.user.app_metadata.user_guid;
  const connectionType = event.connection.name;
  console.log('Connection: ' + JSON.stringify(event.connection));

  if (connectionType === GOOGLE_CONNECTION_IDENTIFIER) {
    if (pepperUserGuid) {
      console.log('All good, user has pepper_user_guid: ' + pepperUserGuid);
      return;
    } else {
      /**
       * User was not registered in pepper yet
       */
      console.log('Nooo, there is no pepper user guid...', { pepperUserGuid });
      console.log('User', JSON.stringify(event.user));
      console.log('Event', JSON.stringify(event));

      const request = event.request || {};
      const query = request.query || {};

      const tempUserGuid = query.temp_user_guid;

      if (!tempUserGuid) {
        console.log('There is no temp user guid...', { tempUserGuid });

        const loginErrPayload = {
          code: 'prequal_skipped',
          message: 'You have to complete prequal first in order to proceed',
          statusCode: 422,
        };
        return api.access.deny(JSON.stringify(loginErrPayload));
      } else {
        console.log('Temp user guid is set to: ' + tempUserGuid);
        return;
      }
    }
  } else {
    return;
  }
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
