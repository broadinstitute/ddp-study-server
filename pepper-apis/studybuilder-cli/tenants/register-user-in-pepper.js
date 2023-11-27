function (user, context, callback) {
    const {Logging} = require('@google-cloud/logging');
    const cloudLoggingEnabled = !!configuration.googleApplicationCredentials;
    var cloudLog = null;

    if (cloudLoggingEnabled) {
        const cloudLogName = "_Default";
        var applicationCredentials = JSON.parse(configuration.googleApplicationCredentials);

        console.log("Successfully loaded googleApplicationCredentials. Google Cloud Logging to project " + applicationCredentials.project_id + " is enabled");

        var cloudLoggingConfig = {
            projectId: applicationCredentials.project_id,
            credentials: applicationCredentials
        };

        var cloudLogging = new Logging(cloudLoggingConfig);
        cloudLog = cloudLogging.log(cloudLogName);
    }

    // Environment stabilization. This will save us a number of
    // overly complex if statements later
    context.clientMetadata = context.clientMetadata || {};

    context.request = context.request || {};
    context.request.body = context.request.body || {};
    context.request.geoip = context.request.geoip || {};
    context.request.query = context.request.query || {};

    user.app_metadata = user.app_metadata || {};
    user.app_metadata.pepper_user_guids = user.app_metadata.pepper_user_guids || {};

    // Use of the m2mClients list below should be considered legacy behavior, and
    // may be removed at any time. Any new clients should set the key 'skipPepperRegistration'
    // to the value of 'true' in their client metadata if the Pepper registration process
    // is not required.
    var m2mClients = ['dsm', 'Count Me In (Salt CMS)'];

    // The new flag is opt-in. If no value is defined, the legacy behavior will be used.
    // If the value is non-null, then assume the client has opted in.
    var skipPepperRegistration = context.clientMetadata.skipPepperRegistration || null;
    if ((skipPepperRegistration === null) && (m2mClients.includes(context.clientName))) {
        console.log('skipping Pepper registration for legacy client \'' + context.clientName + '\'');
        return callback(null, user, context);
    } else if (skipPepperRegistration === 'true') {
        console.log('skipping Pepper registration for \'' + context.clientName + '\'');
        return callback(null, user, context);
    }

    var pepperUserGuidClaim = 'https://datadonationplatform.org/uid';
    var pepperClientClaim = 'https://datadonationplatform.org/cid';
    var pepperTenantClaim = 'https://datadonationplatform.org/t';

    var tenantDomain = 'https://' + auth0.domain + '/';
    context.idToken[pepperTenantClaim] = tenantDomain;
    context.idToken[pepperClientClaim] = context.clientID;

    var mockRegistration = context.clientMetadata.mockRegistration;
    var doLocalRegistration = context.clientMetadata.doLocalRegistration;

    if (mockRegistration) {
        var overrideUserGuid;
        if (user.app_metadata.testingGuid) {
            overrideUserGuid = user.app_metadata.testingGuid;
            console.log('Using hardcoded guid ' + overrideUserGuid + ' during registration for ' + user.user_id + ' for client ' + context.clientID);
        } else if (context.clientMetadata.overrideUserGuid) {
            overrideUserGuid = context.clientMetadata.overrideUserGuid;
            console.log('Using hardcoded guid ' + overrideUserGuid + ' during registration for ' + user.user_id + ' for client ' + context.clientID);
        } else {
            console.log('No hardcoded guid found during mock registration for ' + user.user_id + ' from client ' + context.clientID);
        }

        context.idToken[pepperUserGuidClaim] = overrideUserGuid;
        return callback(null, user, context);
    } else if (doLocalRegistration) {
        var localRegistrationGuid = user.app_metadata.pepper_user_guids[context.clientID];

        if (localRegistrationGuid) {
            console.log('Using guid ' + localRegistrationGuid + ' during local registration for ' + user.user_id + ' for client ' + context.clientID);
            context.idToken[pepperUserGuidClaim] = localRegistrationGuid;
        } else {
            console.log('No pepper guid available during local registration for ' + user.user_id + ' for client ' + context.clientID);
        }

        return callback(null, user, context);
    } else {
        var pepper_params = {
            auth0UserId: user.user_id,
            auth0ClientId: context.clientID,
            auth0Domain: tenantDomain
        };

        if (context.request.query.study_guid) {
            pepper_params.studyGuid = context.request.query.study_guid;
            console.log('StudyGuid passed in (via query) = ' + pepper_params.studyGuid);
        } else if (context.request.body.study_guid) {
            pepper_params.studyGuid = context.request.body.study_guid;
            console.log('StudyGuid passed in (via body) = ' + pepper_params.studyGuid);
        } else if (context.clientMetadata.study) {
            pepper_params.studyGuid = context.clientMetadata.study;
            console.log('StudyGuid (defaulting to clientMetadata.study) = ' + pepper_params.studyGuid);
        } else {
            console.log('No studyGuid passed in request');
        }

        if (context.request.query.temp_user_guid) {
            pepper_params.tempUserGuid = context.request.query.temp_user_guid;
            console.log('Temp user guid passed in (via query) = ' + pepper_params.tempUserGuid);
        } else if (context.request.body.temp_user_guid) {
            pepper_params.tempUserGuid = context.request.body.temp_user_guid;
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
            !user.app_metadata.user_guid &&
            user.user_metadata && !!user.user_metadata.temp_user_guid
        ) {
            pepper_params.tempUserGuid = user.user_metadata.temp_user_guid;
        }

        if (context.request.query.mode) {
            pepper_params.mode = context.request.query.mode;
            console.log('Registration Mode passed in (via query) = ' + pepper_params.mode);
        } else if (context.request.body.mode) {
            pepper_params.mode = context.request.body.mode;
            console.log('Registration Mode passed in (via body) = ' + pepper_params.mode);
        } else {
            console.log('No Registration Mode passed in request');
        }

        if (pepper_params.mode && pepper_params.mode === "login" &&
            !user.app_metadata.user_guid && !user.app_metadata.pepper_user_guids &&
            !pepper_params.tempUserGuid
        ) {
            console.log('User not registered');
            const loginErrPayload = {
                code: 'user_not_registered',
                message: 'User need to register first in order to login',
                statusCode: 403,
            };
            const loginErr = new Error(JSON.stringify(loginErrPayload));
            return callback(loginErr, user, context);
        }

        if (context.request.query.invitation_id) {
            pepper_params.invitationId = context.request.query.invitation_id;
            console.log('Invitation id passed in (via query) = ' + pepper_params.invitationId);
        } else if (context.request.body.invitation_id) {
            pepper_params.invitationId = context.request.body.invitation_id;
            console.log('Invitation id passed in (via body) = ' + pepper_params.invitationId);
        }

        if (context.request.query.language) {
            pepper_params.languageCode = context.request.query.language;
            console.log('User language passed in (via query) = ' + pepper_params.languageCode);
        } else if (context.request.body.language) {
            pepper_params.languageCode = context.request.body.language;
            console.log('User language passed in (via body) = ' + pepper_params.languageCode);
        }

        if (context.request.query.time_zone) {
            pepper_params.timeZone = context.request.query.time_zone;
            console.log('User timezone passed in (via query) = ' + pepper_params.timeZone);
        } else if (context.request.body.time_zone) {
            pepper_params.timeZone = context.request.body.time_zone;
            console.log('User timezone passed in (via body) = ' + pepper_params.timeZone);
        }

        // This is the token renewal case. Let's avoid going through pepper registration
        if (context.request.query.renew_token_only) {
            context.idToken[pepperUserGuidClaim] = user.app_metadata.user_guid;
            return callback(null, user, context);
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
        var isRefreshTokenExchange = (context.protocol === "oauth2-refresh-token");
        var needsStudyRegistration = !!(pepper_params.studyGuid);
        var hasCachedUserGuid = !!(user.app_metadata.user_guid);
        if (isRefreshTokenExchange && hasCachedUserGuid && !needsStudyRegistration) {
            console.log('using app_metadata cached user GUID');
            context.idToken[pepperUserGuidClaim] = user.app_metadata.user_guid;
            return callback(null, user, context);
        } else {
            user.user_metadata = user.user_metadata || {};
            if (user.user_metadata.first_name) {
                pepper_params.firstName = user.user_metadata.first_name;
                console.log('User metadata has first name = ' + pepper_params.firstName);
            }
            if (user.user_metadata.last_name) {
                pepper_params.lastName = user.user_metadata.last_name;
                console.log('User metadata has last name = ' + pepper_params.lastName);
            }

            if (cloudLoggingEnabled) {
                var severity = "INFO";

                if (pepper_params.mode) {
                    if ( (pepper_params.mode === 'signup') && (!pepper_params.tempUserGuid) ) {
                        severity = "ERROR";
                    }
                }

                var entry = cloudLog.entry({
                    severity: severity,
                    labels: {
                        source: "auth0",
                        mode: pepper_params.mode || "default"
                    }
                }, context);

                cloudLog.write(entry);
            } else {
                console.log(context);
            }
            
            request.post({
                url: configuration.pepperBaseUrl + '/pepper/v1/register',
                json: pepper_params,
                timeout: 15000
            }, function(err, response, body) {
                if (err) {
                    console.log('Error while registering auth0 user ' + user.user_id);
                    console.log(err);
                    let error = new Error(JSON.stringify({
                        code: err.code,
                        message: err.message,
                        statusCode: 500
                    }));
                    return callback(error);
                } else if (response && response.statusCode !== 200) {
                    console.log('Failed to register auth0 user ' + user.user_id + ':' + response.statusCode);
                    console.log(body);
                    body = body || {};
                    let error = new Error(JSON.stringify({
                        code: body.code,
                        message: body.message,
                        statusCode: response.statusCode
                    }));
                    return callback(error);
                } else {
                    // all is well
                    var ddpUserGuid = body.ddpUserGuid;
                    user.app_metadata.user_guid = ddpUserGuid;
                    auth0.users.updateAppMetadata(user.user_id, user.app_metadata)
                        .then(function(){
                            context.idToken[pepperUserGuidClaim] = ddpUserGuid;
                            console.log('Registered pepper user ' + ddpUserGuid + ' for auth0 user ' + user.user_id);
                            return callback(null, user, context);
                        })
                        .catch(function(err){
                            console.log('Error updating metadata for auth0 user ' + user.user_id);
                            console.log(err);
                            let error = new Error(JSON.stringify({
                                code: err.code,
                                message: err.message,
                                statusCode: 500
                            }));
                            return callback(error);
                        });
                }
            });
        }
    }
}
