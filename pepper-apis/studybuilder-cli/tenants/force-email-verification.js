function forceEmailVerification(user, context, callback) {
  if (!user.email_verified) {
    const errPayload = {
      code: 'email_verification_required',
      statusCode: 401,
      message: 'Email is not verified',
    };

    console.log(`
      Attempt to sign in with an email which is not verified.
      Throwing an error: ${JSON.stringify(errPayload, null, 2)}
    `);

    return callback(new Error(JSON.stringify(errPayload)));
  } else {
    return callback(null, user, context);
  }
}
