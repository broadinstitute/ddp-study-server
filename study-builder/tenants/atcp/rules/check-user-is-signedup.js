function (user, context, callback) {
    context.request = context.request || {};
  console.log('Query received: ' + JSON.stringify(context.request.query));
  console.log('User info received:' + JSON.stringify(user));
  if (user.identities[0].isSocial && context.request.query.mode === 'login')
  {
    user.app_metadata = user.app_metadata || {};
    user.app_metadata.pepper_user_guids = user.app_metadata.pepper_user_guids || {};
    var pepper_user_guid = user.app_metadata.pepper_user_guids[context.clientID];
    if (!!pepper_user_guid)
    {
      console.log('user should sign up');
     let error = new Error(JSON.stringify({
                              code: 'unauthorized',
                              message: 'signup required',
                              statusCode: 500
                          }));
                          return callback(error);
    }
  }
 //   return callback(null, user, context);
}
