package org.broadinstitute.lddp.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.exception.InvalidTokenException;
import org.broadinstitute.lddp.exception.ValidationException;
import org.broadinstitute.lddp.handlers.util.*;
import org.broadinstitute.lddp.security.Auth0Util;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.broadinstitute.lddp.util.CheckValidity;
import org.broadinstitute.lddp.util.EDCClient;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Base class for request handling.
 */
public abstract class AbstractRequestHandler<V extends CheckValidity> extends HandlerUtil implements RequestHandler<V>, Route
{
    private static final Logger logger = LoggerFactory.getLogger(AbstractRequestHandler.class);

    public static final String JSON_PARTICIPANT_ID = "participantId";
    public static final String JSON_SURVEY_URL = "surveyUrl";
    public static final String JSON_STATUS = "surveyStatus";

    private Class<V> valueClass;
    protected EDCClient edc;
    protected Config config;
    protected Map<String, Object> systemSecrets;
    protected Auth0Util auth0Util = null;

    public AbstractRequestHandler(@NonNull Class<V> valueClass, @NonNull EDCClient edc, @NonNull Config config)
    {
        this.valueClass = valueClass;
        this.edc = edc;
        this.config = config;
        setup();
    }

    public AbstractRequestHandler(@NonNull Class<V> valueClass, @NonNull Config config)
    {
        this.valueClass = valueClass;
        this.config = config;
        setup();
    }

    private void setup() {
        setSystemBasedSecrets();
        auth0Util = Auth0Util.configureAuth0Util(config);
    }

    @Override
    public Object handle(@NonNull Request request, @NonNull Response response)
    {
        Result result = null;
        try
        {
            //get the token if it is there
            String tokenFromHeader = Utility.getTokenFromHeader(request);

            //first authenticate here if we need to (if :system is in path)
            if (!request.params().isEmpty()&&request.params().containsKey(":system")) {
                String system = request.params().get(":system");
                performSystemBasedAuth(system, tokenFromHeader);
            }

            ObjectMapper objectMapper = new ObjectMapper();

            V value = null;

            //POSTS
            if (valueClass == RawPayload.class) { //RawPayload used in weird handlers for "lite" DDPs
                value = (V)new RawPayload(request.body());
            }
            else if (valueClass != EmptyPayload.class) {
                value = objectMapper.readValue(request.body(), valueClass);
            }

            //GET
            result = process(value, request.queryMap(), request.params(), request.requestMethod(), tokenFromHeader, response);

            HandlerUtil.setResponseType(request, response);

            //return a file
            if (result.getInputStream() != null) {
                HttpServletResponse raw = response.raw();

                byte[] buffer = new byte[1024 * 4];
                int bytesRead;
                while ((bytesRead = result.getInputStream().read(buffer)) != -1)
                {
                    raw.getOutputStream().write(buffer, 0, bytesRead);
                }

                raw.getOutputStream().flush();
            }
        }
        catch (InvalidTokenException tokenEx) {
            result = new Result(401, SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString());
        }
        catch (Exception ex) {
            logger.error("PROCESS REQUEST - Error: ", ex);
            result = new Result(500);
        }
        finally {
            //perform cleanup
            if ((result != null)&&(result.getInputStream() != null))
            {
                try
                {
                    result.getInputStream().close();
                }
                catch (Exception ex)
                {
                    logger.error("PROCESS REQUEST - Error closing input stream: ", ex);
                    result = new Result(500);
                }
            }
        }

        response.status(result.getCode());

        //set this in case an after filter needs to read it
        response.body(result.getBody());

        return result.getBody();
    }

    public final Result process(V value, QueryParamsMap queryParams, Map<String, String> pathParams, String requestMethod, String token,
                               Response response)  {
        if (value != null && !value.isValid()) {
            throw new ValidationException(valueClass.toString() + " failed validation.");
        } else {
            return processRequest(value, queryParams, pathParams, requestMethod, token, response);
        }
    }

    protected abstract Result processRequest(V value, QueryParamsMap queryParams, Map<String,String> pathParams, String requestMethod, String token,
                                             Response response);

    private void performSystemBasedAuth(@NonNull String system, @NonNull String tokenFromHeader) {
        boolean isTokenValid = false;

        if (StringUtils.isNotBlank(tokenFromHeader)) {
            isTokenValid = SecurityHelper.verifyNonUIToken((String)systemSecrets.get(system), tokenFromHeader, false);
        }

        if (!isTokenValid) {
            throw new InvalidTokenException("Invalid token.");
        }
    }

    private void setSystemBasedSecrets() {
        if (config.hasPath("portal.jwtSystemSecrets")) {
            systemSecrets = config.getObject("portal.jwtSystemSecrets").unwrapped();
        }
    }
}
