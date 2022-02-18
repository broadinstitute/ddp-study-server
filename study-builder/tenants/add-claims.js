function addEmailToAccessToken(user, context, callback) {
    // This rule adds userid, client and tenant claims to the access token.

    var pepperUserGuidClaim = 'https://datadonationplatform.org/uid';
    var pepperClientClaim = 'https://datadonationplatform.org/cid';
    var pepperTenantClaim = 'https://datadonationplatform.org/t';

    var tenantDomain = 'https://' + auth0.domain + '/';
    context.idToken[pepperTenantClaim] = tenantDomain;
    context.idToken[pepperClientClaim] = context.clientID;

    return callback(null, user, context);
}
