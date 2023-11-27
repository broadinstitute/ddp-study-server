function emailVerified(user, context, callback) {
  console.log('Received user', user);
  console.log('Received context', context);
  
  if (!user.email_verified) {
    const ManagementClient = require('auth0@2.9.1').ManagementClient;
    const management = new ManagementClient({
      token: auth0.accessToken,
      domain: auth0.domain,
    });
    const params = {
      user_id: user.user_id,
      client_id: context.clientID,
    };

    console.log('Attempt to resend a confirmation email');

    management.jobs.verifyEmail(params, function (err) {
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

      return callback(error);
    });
  } else {
    return callback(null, user, context);
  }
}
