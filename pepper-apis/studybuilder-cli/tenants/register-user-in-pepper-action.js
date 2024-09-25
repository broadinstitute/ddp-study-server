/**
 * This Action was migrated from Rule.
 * Rule name: Register User in Pepper
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
  console.log('In action register pepper user');

  /**
   * The following code block will skip this Action if Rule "Register User in Pepper"
   * was previously executed in the transaction in order to avoid duplication
   * of logic.
   */
  if (api.rules.wasExecuted("rul_*")) {
    console.log('In action register pepper user rule executed');
    return;
  }

  const {Logging} = require('@google-cloud/logging');
  const cloudLoggingEnabled = false; //!!event.secrets.googleApplicationCredentials;
  var cloudLog = null;

  if (cloudLoggingEnabled) {
    console.log('cloudLogging Enabled');

    const cloudLogName = "_Default";
    var applicationCredentials = JSON.parse(event.secrets.googleApplicationCredentials);

    console.log("Successfully loaded googleApplicationCredentials. Google Cloud Logging to project " + applicationCredentials.project_id + " is enabled");

    var cloudLoggingConfig = {
      projectId: applicationCredentials.project_id,
      credentials: applicationCredentials
    };

    var cloudLogging = new Logging(cloudLoggingConfig);
    cloudLog = cloudLogging.log(cloudLogName);
  } else {
    console.log('cloudLogging Disabled');
  }

  // Use of the m2mClients list below should be considered legacy behavior, and
  // may be removed at any time. Any new clients should set the key 'skipPepperRegistration'
  // to the value of 'true' in their client metadata if the Pepper registration process
  // is not required.
  var m2mClients = ['dsm', 'Count Me In (Salt CMS)'];

  // The new flag is opt-in. If no value is defined, the legacy behavior will be used.
  // If the value is non-null, then assume the client has opted in.
  var skipPepperRegistration = event.client.metadata.skipPepperRegistration || null;
  if ((skipPepperRegistration === null) && (m2mClients.includes(event.client.name))) {
    console.log('skipping Pepper registration for legacy client \'' + event.client.name + '\'');
    return;
  } else if (skipPepperRegistration === 'true') {
    console.log('skipping Pepper registration for \'' + event.client.name + '\'');
    return;
  }

  var pepperUserGuidClaim = 'https://datadonationplatform.org/uid';
  var pepperClientClaim = 'https://datadonationplatform.org/cid';
  var pepperTenantClaim = 'https://datadonationplatform.org/t';
  var tenantDomain = 'https://' + event.secrets.domain + '/';

  api.idToken.setCustomClaim(pepperClientClaim, event.client.client_id);
  api.idToken.setCustomClaim(pepperTenantClaim, tenantDomain);

  var mockRegistration = event.client.metadata.mockRegistration || null;
  var doLocalRegistration = event.client.metadata.doLocalRegistration || null;
  if (mockRegistration != null && mockRegistration) {
    console.log('in mockRegistration');
    var overrideUserGuid;
    if (event.user.app_metadata.testingGuid) {
      overrideUserGuid = event.user.app_metadata.testingGuid;
      console.log('Using hardcoded guid ' + overrideUserGuid + ' during registration for ' + event.user.user_id + ' for client ' + event.client.client_id);
    } else if (event.client.metadata.overrideUserGuid) {
      overrideUserGuid = event.client.metadata.overrideUserGuid;
      console.log('Using hardcoded guid ' + overrideUserGuid + ' during registration for ' + event.user.user_id + ' for client ' + event.client.client_id);
    } else {
      console.log('No hardcoded guid found during mock registration for ' + event.user.user_id + ' from client ' + event.client.client_id);
    }
    api.idToken.setCustomClaim(pepperUserGuidClaim, overrideUserGuid);
    return;
  } else if (doLocalRegistration != null && doLocalRegistration) {
    var localRegistrationGuid = null;
    if (event.user.app_metadata.pepper_user_guids) {
      console.log('has user.app_metadata..pepper_user_guids');
      localRegistrationGuid = event.user.app_metadata.pepper_user_guids[event.client.client_id];
    }
    if (localRegistrationGuid != null && localRegistrationGuid) {
      console.log('Using guid ' + localRegistrationGuid + ' during local registration for ' + event.user.user_id + ' for client ' + event.client.client_id);
      api.idToken.setCustomClaim(pepperUserGuidClaim, localRegistrationGuid);
    } else {
      console.log('No pepper guid available during local registration for ' + event.user.user_id + ' for client ' + event.client.client_id);
    }
    return;
  } else {
    console.log('Action: registration/login flow...');
    var pepper_params = {
      auth0UserId: event.user.user_id,
      auth0ClientId: event.client.client_id,
      auth0Domain: tenantDomain
    };
    console.log('pepper params: ' + JSON.stringify(pepper_params));

    console.log('looking for study_guid');
    if (event.request.query.study_guid) {
      pepper_params.studyGuid = event.request.query.study_guid;
      console.log('StudyGuid passed in (via query) = ' + pepper_params.studyGuid);
    } else if (event.request.body.study_guid) {
      pepper_params.studyGuid = event.request.body.study_guid;
      console.log('StudyGuid passed in (via body) = ' + pepper_params.studyGuid);
    } else if (event.client.metadata.study) {
      pepper_params.studyGuid = event.client.metadata.study;
      console.log('StudyGuid (defaulting to clientMetadata.study) = ' + pepper_params.studyGuid);
    } else {
      console.log('No studyGuid passed in request');
    }

    console.log('looking for temp_user_guid');
    if (event.request.query.temp_user_guid) {
      pepper_params.tempUserGuid = event.request.query.temp_user_guid;
      console.log('Temp user guid passed in (via query) = ' + pepper_params.tempUserGuid);
    } else if (event.request.body.temp_user_guid) {
      pepper_params.tempUserGuid = event.request.body.temp_user_guid;
      console.log('Temp user guid passed in (via body) = ' + pepper_params.tempUserGuid);
    } else {
      console.log('No temp user guid passed in request');
    }

    /**
     * If `tempUserGuid` was not set with value from request
     * AND user is not yet registered in pepper (`user.app_metadata.user_guid` is empty)
     * take `tempUserGuid` from `user_metadata` (if one exists)
     */
    if (
      !pepper_params.tempUserGuid &&
      !event.user.app_metadata.user_guid &&
      event.user.user_metadata && !!event.user.user_metadata.temp_user_guid
    ) {
      console.log('looking for temp_user_guid in user_metadata.temp_user_guid: ' + event.user.user_metadata.temp_user_guid);
      pepper_params.tempUserGuid = event.user.user_metadata.temp_user_guid;
    }

    if (event.request.query.mode) {
      pepper_params.mode = event.request.query.mode;
      console.log('Registration Mode passed in (via query) = ' + pepper_params.mode);
    } else if (event.request.body.mode) {
      pepper_params.mode = event.request.body.mode;
      console.log('Registration Mode passed in (via body) = ' + pepper_params.mode);
    } else {
      console.log('No Registration Mode passed in request');
    }

    if (pepper_params.mode && pepper_params.mode === "login" &&
      !event.user.app_metadata.user_guid && !event.user.app_metadata.pepper_user_guids &&
      !pepper_params.tempUserGuid
    ) {
      console.log('User not registered');
      const loginErrPayload = {
        code: 'user_not_registered',
        message: 'User need to register first in order to login',
        statusCode: 403,
      };
      return api.access.deny(loginErrPayload.message);
    }

    if (event.request.query.invitation_id) {
      pepper_params.invitationId = event.request.query.invitation_id;
      console.log('Invitation id passed in (via query) = ' + pepper_params.invitationId);
    } else if (event.request.body.invitation_id) {
      pepper_params.invitationId = event.request.body.invitation_id;
      console.log('Invitation id passed in (via body) = ' + pepper_params.invitationId);
    }

    if (event.request.query.language) {
      pepper_params.languageCode = event.request.query.language;
      console.log('User language passed in (via query) = ' + pepper_params.languageCode);
    } else if (event.request.body.language) {
      pepper_params.languageCode = event.request.body.language;
      console.log('User language passed in (via body) = ' + pepper_params.languageCode);
    }

    if (event.request.query.time_zone) {
      pepper_params.timeZone = event.request.query.time_zone;
      console.log('User timezone passed in (via query) = ' + pepper_params.timeZone);
    } else if (event.request.body.time_zone) {
      pepper_params.timeZone = event.request.body.time_zone;
      console.log('User timezone passed in (via body) = ' + pepper_params.timeZone);
    }

    // This is the token renewal case. Let's avoid going through pepper registration
    if (event.request.query.renew_token_only) {
      console.log('Action request.query.renew_token_only: ');
      api.idToken.setCustomClaim(pepperUserGuidClaim, event.user.app_metadata.user_guid);
      return;
    }

    // In order to get a refresh token, the client must go through one of the other methods
    // to get an id/access token first (oauth2-password, oidc-implicit-profile, etc).
    // This allows us to assume that, if they have a refresh token, they must have gone through the
    // registration process.
    //
    // Errata: This assumption is not valid in a multi-study world. It's possible to fetch a token
    //  for one study initially, then attempt to renew it when accessing another. For the moment,
    //  assume that, if a study guid is included with the call, the client wants us to call the
    //  registration endpoint.
    //var isRefreshTokenExchange = event.transaction.protocol  === "oauth2-refresh-token"; //todo
    var isRefreshTokenExchange = false;
    console.log('Action Event isRefreshTokenExchange::' + isRefreshTokenExchange);
    var needsStudyRegistration = !!(pepper_params.studyGuid);
    var hasCachedUserGuid = !!(event.user.app_metadata.user_guid);
    //if (isRefreshTokenExchange && hasCachedUserGuid && !needsStudyRegistration) { //todo
    if (hasCachedUserGuid && !needsStudyRegistration) {
      console.log('using app_metadata cached user GUID: ' + JSON.stringify(event.user.app_metadata));
      api.idToken.setCustomClaim(pepperUserGuidClaim, event.user.app_metadata.user_guid);
      return;
    } else {
      if (event.user.user_metadata.first_name) {
        pepper_params.firstName = event.user.user_metadata.first_name;
        console.log('User metadata has first name = ' + pepper_params.firstName);
      }
      if (event.user.user_metadata.last_name) {
        pepper_params.lastName = event.user.user_metadata.last_name;
        console.log('User metadata has last name = ' + pepper_params.lastName);
      }

      if (cloudLoggingEnabled) {
        console.log('trying to log to cloud');
        var severity = "INFO";

        if (pepper_params.mode) {
          if ((pepper_params.mode === 'signup') && (!pepper_params.tempUserGuid)) {
            severity = "ERROR";
          }
        }

        var entry = cloudLog.entry({
          severity: severity,
          labels: {
            source: "auth0",
            mode: pepper_params.mode || "default"
          }
        }, event);

        cloudLog.write(entry);

      }

      //added below in action as workaround for not able to update userGUID in user AppMetadata post registration
      // todo revisit and fix
      console.log('pepper params: ' + JSON.stringify(pepper_params));
      console.debug('EVENT: ' + JSON.stringify(event));
      console.debug('API::: ' + JSON.stringify(api));
      if (event.user.app_metadata.user_guid) {
        console.log('setting userGUID claim from user.app_metadata');
        api.idToken.setCustomClaim(pepperUserGuidClaim, event.user.app_metadata.user_guid);
      } else if (pepper_params.tempUserGuid) {
        console.log('setting userGUID claim from temp user');
        api.idToken.setCustomClaim(pepperUserGuidClaim, pepper_params.tempUserGuid);
        api.user.setAppMetadata("user_guid", pepper_params.tempUserGuid);
      }

      var pepperUrl = event.secrets.pepperBaseUrl;
      if (event.client.metadata.backendUrl) {
        pepperUrl = event.client.metadata.backendUrl;
      }
      var request = require('request');
      console.log('Invoking DDP registration : ' + pepperUrl + ' ... params: ' + JSON.stringify(pepper_params));
      console.log('ID Token:' + JSON.stringify(api.idToken));
      request.post({
        url: pepperUrl + '/pepper/v1/register',
        json: pepper_params,
        timeout: 15000
      }, function (err, response, body) {
        if (err) {
          console.log('Error while registering auth0 user ' + event.user.user_id);
          console.log(err);
          let error = new Error(JSON.stringify({
            code: err.code,
            message: err.message,
            statusCode: 500
          }));
          console.log('Error during registration: ' + err.message);
          return api.access.deny(err.message);
        } else if (response && response.statusCode !== 200) {
          console.log('Response: ' + JSON.stringify(response));
          console.log('Failed to register auth0 user ' + event.user.user_id + ':' + response.statusCode + ' at ' + pepperUrl);
          console.log('BODY: ' + JSON.stringify(body));
          body = body || {};
          let error = new Error(JSON.stringify({
            code: body.code,
            message: body.message,
            statusCode: response.statusCode
          }));

          console.log('Access denied...' + body.message);
          return api.access.deny(body.message);
          //todo .. doesnt seem to block UI invoking next steps?
        } else {
          console.log(' register response: ' + JSON.stringify(response));
          console.log(' register body: ' + JSON.stringify(body));

          // all is well
          //todo.. below doesnt seem to work..added before register call for now
          //maybe try API call?
          var ddpUserGuid = body.ddpUserGuid;
          api.idToken.setCustomClaim(pepperUserGuidClaim, ddpUserGuid);
          api.user.setAppMetadata("user_guid", ddpUserGuid);
          api.user.setUserMetadata("user_guid", ddpUserGuid);
          console.log('updated user metaData with user GUID ? ');

          // Update user AppMetadata with user_guid using Auth0 Management API
          const auth0Sdk = require("auth0");
          const ManagementClient = auth0Sdk.ManagementClient;
          // This will make an Authentication API call
          const managementClientInstance = new ManagementClient({
            // These come from a machine-to-machine application
            domain: event.secrets.domain,
            token: api.accessToken,
            //clientId: event.secrets.M2M_CLIENT_ID,
            //clientSecret: event.secrets.M2M_CLIENT_SECRET,
            scope: "update:users"
          });

          var params = {id: event.user.user_id};
          var metadata = {
            user_guid: ddpUserGuid
          };

          console.log('managementClientInstance userID: ' + JSON.stringify(event.user.user_id));
          managementClientInstance.users.updateAppMetadata(params, metadata, function (err, user) {
            if (err) {
              console.log(
                'Post registration user AppMetadata update failed:' + err.getMessage());
              return api.access.deny(err.message);

            } else {
              console.log('Successfully completed Post registration user AppMetadata update');
            }
          });
        }

      });
    }
  }
};


/**
 * Handler that will be invoked when this action is resuming after an external redirect. If your
 * onExecutePostLogin function does not perform a redirect, this function can be safely ignored.
 *
 * @param {Event} event - Details about the user and the context in which they are logging in.
 * @param {PostLoginAPI} api - Interface whose methods can be used to change the behavior of the login.
 */
exports.onContinuePostLogin = async (event, api) => {
  console.log('In action register pepper user onContinuePostLogin..');
};
