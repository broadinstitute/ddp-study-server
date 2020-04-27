function (user, context, callback) {
    // Environment stabilization. This will save us a number of
    // overly complex if statements later
    context.clientMetadata = context.clientMetadata || {};

    context.request = context.request || {};
    context.request.body = context.request.body || {};
    context.request.geoip = context.request.geoip || {};
    context.request.query = context.request.query || {};

    user.app_metadata = user.app_metadata || {};
    user.app_metadata.pepper_user_guids = user.app_metadata.pepper_user_guids || {};

    var m2mClients = ['dsm', 'Count Me In (Salt CMS)'];
    if (m2mClients.includes(context.clientName)) {
        return callback(null, user, context);
    }

    var pepperUserGuidClaim = 'https://datadonationplatform.org/uid';
    var pepperClientClaim = 'https://datadonationplatform.org/cid';
    var pepperTenantClaim = 'https://datadonationplatform.org/t';

    var tenantDomain = 'https://' + context.tenant + '.auth0.com/';
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
            auth0Domain: tenantDomain,
            auth0ClientCountryCode: 'us'
        };

        if (context.request.geoip.country_code) {
            pepper_params.auth0ClientCountryCode = context.request.geoip.country_code;
            console.log('Using country code (via auth0) = ' + pepper_params.auth0ClientCountryCode);
        }

        if (context.request.query.study_guid) {
            pepper_params.studyGuid = context.request.query.study_guid;
            console.log('StudyGuid passed in (via query) = ' + pepper_params.studyGuid);
        } else if (context.request.body.study_guid) {
            pepper_params.studyGuid = context.request.body.study_guid;
            console.log('StudyGuid passed in (via body) = ' + pepper_params.studyGuid);
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

        if (context.request.query.mode) {
            pepper_params.mode = context.request.query.mode;
            console.log('Registration Mode passed in (via query) = ' + pepper_params.mode);
        } else if (context.request.body.mode) {
            pepper_params.mode = context.request.body.mode;
            console.log('Registration Mode passed in (via body) = ' + pepper_params.mode);
        } else {
            console.log('No Registration Mode passed in request');
        }

        if (context.request.query.invitation_id) {
            pepper_params.invitationId = context.request.query.invitation_id;
        } else if (context.request.body.invitation_id) {
            pepper_params.invitationId = context.request.body.invitation_id;
        }

        console.log(context);

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
