// This rule will look for admin account and link it to the Google social account. Account link is
// determined by using the email. This is only executed for the admin client and only if the
// accounts are not already linked.
//
// This was derived from Auth0's rule template from here:
// https://github.com/auth0/rules/blob/b381ef4a1d5df16c1716ea066f46cff87b88f7f1/src/rules/link-users-by-email-with-metadata.js
//
// This assumes the rule runtime is Node 12, which is configured in Auth0 Dashboard Tenant Settings.
function(user, context, callback) {
  const _ = require('lodash');
  const request = require('request');
  const adminConnection = 'study-admin-authentication';
  const googleConnection = 'google-oauth2';

  // Note that client metadata key/values are all strings.
  context.clientMetadata = context.clientMetadata || {};
  const isAdminClient = context.clientMetadata.isAdminClient === 'true';

  if (!isAdminClient || context.connection !== googleConnection) {
    console.log('Either not admin client or not google connection, skipping account linking');
    return callback(null, user, context);
  }

  const adminIdentity = user.identities.find(identity => identity.connection === adminConnection);
  if (adminIdentity) {
    console.log('Current user already has admin identity, meaning google identity has already been linked');
    return callback(null, user, context);
  }

  request({
    url: auth0.baseUrl + '/users',
    headers: {
      Authorization: 'Bearer ' + auth0.accessToken
    },
    qs: {
      search_engine: 'v3',
      q: `identities.connection:"${adminConnection}" AND email:"${user.email}"`
    }
  }, function(err, response, body) {
    if (err) {
      console.log('Error while searching for admin user with email:', user.email);
      console.log(err);
      return callback(err);
    }

    if (response.statusCode !== 200) {
      console.log('Failed to search for admin user with email:', user.email);
      console.log(body);
      return callback(new Error(body));
    }

    const data = JSON.parse(body);
    if (data.length !== 1) {
      console.log('Expected to find one admin user but found', data.length);
      return callback(new Error("Could not find a corresponding study admin account"));
    }

    const adminUser = data[0];
    const provider = user.identities[0].provider;
    const providerUserId = user.identities[0].user_id;

    console.log('Current user:', user);
    console.log('Found admin user:', adminUser);

    const mergeCustomizer = function(objectValue, sourceValue) {
      if (_.isArray(objectValue)) {
        return sourceValue.concat(objectValue);
      }
    };
    const mergedUserMetadata = _.merge({}, user.user_metadata, adminUser.user_metadata, mergeCustomizer);
    const mergedAppMetadata = _.merge({}, user.app_metadata, adminUser.app_metadata, mergeCustomizer);

    console.log('Merged user_metadata:', mergedUserMetadata);
    console.log('Merged app_metadata:', mergedAppMetadata);

    auth0.users.updateUserMetadata(adminUser.user_id, mergedUserMetadata)
      .then(auth0.users.updateAppMetadata(adminUser.user_id, mergedAppMetadata))
      .then(function() {
        request.post({
          url: auth0.baseUrl + '/users/' + adminUser.user_id + '/identities',
          headers: {
            Authorization: 'Bearer ' + auth0.accessToken
          },
          json: {
            provider: provider,
            user_id: String(providerUserId)
          }
        }, function(err, response, body) {
          if (err) {
            console.log('Error while linking identities');
            console.log(err);
            return callback(err);
          }

          if (response.statusCode >= 400) {
            console.log('Failed to link identities');
            console.log(body);
            return callback(new Error(body));
          }

          console.log(`Successfully linked identity ${user.user_id} to admin user ${adminUser.user_id}`);

          // Setting the primary user seems to be required so that we designate the original admin
          // user is the primary account.
          context.primaryUser = adminUser.user_id;

          // We assume the next rule in the pipeline is Pepper registration. We want the registration
          // to work with the original admin user id, so we pass along the admin user object rather
          // than the current user.
          callback(null, adminUser, context);
        });
      })
      .catch(function(err) {
        console.log('Error while updating metadata');
        console.log(err);
        callback(err);
      });
  });
}
