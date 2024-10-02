exports.onExecutePostLogin = async (event, api) => {
  const ManagementClient = require('auth0').ManagementClient;
  const management = new ManagementClient({
    domain: event.secrets.M2M_DOMAIN,
    clientId: event.secrets.M2M_CLIENT_ID,
    clientSecret: event.secrets.M2M_CLIENT_SECRET
  });

  const verified = event.user.email_verified;

  // I am slicing off the 'auth0|' prefix of the user_id here
  const userIdWithoutAuth0 = event.user.user_id.slice(6);

  const params = {
    client_id: event.client.client_id,
    user_id: event.user.user_id,
    identity: {
      // Passing the corrected user_id here
      user_id: userIdWithoutAuth0,
      provider: 'auth0'
    }
  };

  if (!verified) {
    management.jobs.verifyEmail(params, function (err) {
      if (err) {
        console.log(err)
      } else {
        console.log('Successfully created a job to resend a confirmation email');
      }
    });

    const error = new Error(
      JSON.stringify({
        code: 'unauthorized',
        message: 'You have to confirm your email address before continuing.',
        statusCode: 401,
      })
    );

    return api.access.deny(error.message);
  }
};