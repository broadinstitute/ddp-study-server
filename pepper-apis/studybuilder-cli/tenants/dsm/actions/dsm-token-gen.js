/**
 * Handler that will be called during the execution of a Client Credentials exchange.
 *
 * @param {Event} event - Details about client credentials grant request.
 * @param {CredentialsExchangeAPI} api - Interface whose methods can be used to change the behavior of client credentials grant.
 */
exports.onExecuteCredentialsExchange = async (event, api) => {
  const url = "https://datadonationplatform.org/";
  if (event.request){
    Object.keys(event.request.body).forEach(function(key){
      if (key.indexOf(url) != -1){
        api.accessToken.setCustomClaim(key, event.request.body[key]);
      }
    });
  }
};