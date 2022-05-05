function (user, context, callback) {
  const GOOGLE_CONNECTION_IDENTIFIER = 'google-oauth2';

  const app_metadata = user.app_metadata || {};
  const pepperUserGuid = app_metadata.user_guid;
  const connectionType = context.connection;

  if (connectionType === GOOGLE_CONNECTION_IDENTIFIER) {
    if (pepperUserGuid) {
      console.log('All good, user has pepper_user_guid: ' + pepperUserGuid);

      return callback(null, user, context);
    } else {
      /**
       * User was not registered in pepper yet
       */
      console.log('Nooo, there is no pepper user guid...', { pepperUserGuid });
      console.log('User', user);
      console.log('Context', context);

      const stats = context.stats || {};
      const request = context.request || {};
      const query = request.query || {};

      const loginsCount = stats.loginsCount;
      const tempUserGuid = query.temp_user_guid;

      if (!tempUserGuid) {
        console.log('There is no temp user guid...', { tempUserGuid });

        const loginErrPayload = {
          code: 'prequal_skipped',
          message: 'You have to complete prequal first in order to proceed',
          statusCode: 422,
        };
        const loginErr = new Error(JSON.stringify(loginErrPayload));

        return callback(loginErr, user, context);
      } else {
        console.log('Temp user guid is set to: ' + tempUserGuid);
        
        return callback(null, user, context);
      }
    }
  } else {
    return callback(null, user, context);
  }
}
