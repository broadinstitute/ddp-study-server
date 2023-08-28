package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.route.OncHistoryUploadRoute.canUploadOncHistory;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.onchistory.CodeStudyColumnsProvider;
import org.broadinstitute.dsm.service.onchistory.OncHistoryTemplateService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class OncHistoryTemplateRoute extends RequestHandler {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (!queryParams.hasKey(RoutePath.REALM)) {
            response.status(400);
            return "Request requires realm parameter";
        }
        realm = queryParams.value(RoutePath.REALM);

        if (!canUploadOncHistory(realm, userId)) {
            response.status(403);
            return UserErrorMessages.NO_RIGHTS;
        }

        OncHistoryTemplateService service =
                new OncHistoryTemplateService(realm, new CodeStudyColumnsProvider());
        // write to an intermediate stream then copy to response stream.
        // Spark gets into a mode once the header is set up, and ends up ignoring calls to
        // response.status. So handle the exceptions from the service before setting up
        // the response header (which has to be in place before streaming). BTW, feel
        // free to improve this. The Spark doc is thin, and I did not see any info about
        // this situation via Web search.
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(os);
        zos.putNextEntry(new ZipEntry(service.getTemplateFileName()));
        service.writeTemplate(zos);
        zos.putNextEntry(new ZipEntry(service.getDictionaryFileName()));
        service.writeDictionary(zos);
        zos.closeEntry();
        zos.finish();

        // setup response before we start writing to stream
        String zipFileName = String.format("%s_OncHistoryTemplate.zip", realm);
        response.type(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + zipFileName);
        response.status(200);

        os.writeTo(response.raw().getOutputStream());
        return response.raw();
    }
}
