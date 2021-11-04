package org.broadinstitute.lddp.handlers;

import com.typesafe.config.Config;
import org.broadinstitute.lddp.Recipient;
import org.broadinstitute.lddp.exception.NullValueException;
import org.broadinstitute.lddp.file.BasicProcessor;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.io.InputStream;
import java.util.Map;

public class GetParticipantSmallPdfHandler <T extends SurveyInstance, S extends BasicProcessor> extends AbstractRequestHandler<EmptyPayload>
{

    private static final Logger logger = LoggerFactory.getLogger(GetParticipantSmallPdfHandler.class);

    private static final String LOG_PREFIX = "PROCESS PDF REQUEST - ";

    private static final boolean FLATTEN_ON = true; //always flatten pdfs

    private SurveyService surveyService = new SurveyService();

    public enum PdfType
    {
        consentpdf, releasepdf
    }

    private Class<T> surveyInstanceClass;
    private Class<S> pdfProcessorClass;
    private String surveyPath;
    private boolean requiresUserAuth;

    public GetParticipantSmallPdfHandler(EDCClient edc, Config config, String surveyPath, boolean requiresUserAuth)
    {
        super(EmptyPayload.class, edc, config);

        SurveyConfig surveyConfig = ((DatStatUtil)edc).getSurveyConfigMap().get(surveyPath);
        this.surveyInstanceClass = surveyConfig.getSurveyClass();

        if (surveyConfig.getPdfClass() != null) {
            this.pdfProcessorClass = surveyConfig.getPdfClass();
        }
        else {
            throw new NullValueException("The pdf class is missing for the following survey:" + surveyPath);
        }
        this.surveyPath = surveyPath;
        this.requiresUserAuth = requiresUserAuth;
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response)
    {
        try
        {
            logger.info(LOG_PREFIX + "Start processing request for " + this.surveyInstanceClass + "...");

            DatStatUtil datStatUtil = (DatStatUtil)edc;

            String altPid = pathParams.get(":id");

            Recipient recipient = datStatUtil.getSimpleParticipantInfoByAltPid(altPid);

            if (recipient == null)
            {
                logger.error(LOG_PREFIX + "Unable to generate pdf for participant using id = " + pathParams.get(":id"));
                return new Result(404);
            }

            //make sure we have a good token for this particular participant
            if (requiresUserAuth) {
                if (!SecurityHelper.hasValidPdfAuthorization(config.getString("portal.jwtSecret"), token, altPid)) {
                    return new Result(403, SecurityHelper.ResultType.AUTHORIZATION_ERROR.toString());
                }
            }

            SurveyInstance survey = surveyService.fetchSurveyInstance(datStatUtil, surveyInstanceClass, datStatUtil.getSingleSurveySessionViaSurvey(surveyPath, recipient.getId()));
            BasicProcessor processor = pdfProcessorClass.getConstructor(DatStatUtil.class, boolean.class).newInstance(datStatUtil, FLATTEN_ON);

            InputStream inputStream = processor.generateStream(survey);

            return new Result(200, inputStream);
        }
        catch (Exception e)
        {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}