package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.ddp.Contact;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Arrays;
import java.util.Collection;

public class MailingListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(MailingListRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = request.params(RequestParameter.REALM);
        if (StringUtils.isBlank(realm)) {
            throw new RuntimeException("Realm missing");
        }
        if (UserUtil.checkUserAccess(realm, userId, "mailingList_view", null)) {
            return getMailingListContacts(realm);
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    public Collection<Contact> getMailingListContacts(String realm) {
        DDPInstance instance = DDPInstance.getDDPInstance(realm);

        if (instance == null) {
            throw new RuntimeException("Instance name was not found " + realm);
        }

        Contact[] ddpMailingListContacts = null;
        String sendRequest = instance.getBaseUrl() + RoutePath.DDP_MAILINGLIST_PATH;
        try {
            ddpMailingListContacts = DDPRequestUtil.getResponseObject(Contact[].class, sendRequest, instance.getName(), instance.isHasAuth0Token());
            logger.info("Got " + ddpMailingListContacts.length + " mailing list contacts ");
        }
        catch (Exception ex) {
            throw new RuntimeException("Couldn't get mailing list contacts from " + sendRequest, ex);
        }
        return Arrays.asList(ddpMailingListContacts);
    }
}
