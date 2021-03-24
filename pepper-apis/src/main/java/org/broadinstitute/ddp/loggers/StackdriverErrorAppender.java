package org.broadinstitute.ddp.loggers;

import com.google.cloud.errorreporting.v1beta1.ReportErrorsServiceClient;
import com.google.devtools.clouderrorreporting.v1beta1.*;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.broadinstitute.ddp.util.Utility;

public class StackdriverErrorAppender extends AppenderSkeleton {

    public static final String APPENDER_NAME = "stackdriverError";

    private static String project;
    private static String service;
    private static String instance;
    private static boolean configured = false;

    @Override
    protected void append(LoggingEvent event) {
        if (configured) {
            //we only want to report ERRORs to SD
            if (event.getLevel().toInt() == Level.ERROR_INT) {
                try {
                    try (ReportErrorsServiceClient reportErrorsServiceClient = ReportErrorsServiceClient.create()) {
                        ProjectName projectName = ProjectName.of(project);
                        ServiceContext context = ServiceContext.newBuilder().setService(service).setVersion(instance).build();
                        ReportedErrorEvent errorEvent;

                        String message = event.getRenderedMessage();

                        if ((event.getThrowableInformation() != null)&&(event.getThrowableStrRep().length > 0)) {
                            message = message + " Error\n" + ExceptionUtils.getStackTrace(event.getThrowableInformation().getThrowable());
                            errorEvent = ReportedErrorEvent.newBuilder().setMessage(message).setServiceContext(context).build();
                        }
                        else { //no exception info with error
                            message = message + "\n";
                            SourceLocation location = SourceLocation.newBuilder().
                                    setFilePath(event.getLocationInformation().getFileName()).
                                    setLineNumber(Integer.parseInt(event.getLocationInformation().getLineNumber())).
                                    setFunctionName(event.getLocationInformation().getMethodName()).build();
                            ErrorContext errorContext = ErrorContext.newBuilder().setReportLocation(location).build();
                            errorEvent = ReportedErrorEvent.newBuilder().setContext(errorContext).setMessage(message).setServiceContext(context).build();
                        }

                        reportErrorsServiceClient.reportErrorEvent(projectName, errorEvent);
                    }
                }
                catch (Exception ex) {
                    System.out.println("StackdriverErrorAppender Error: " + ExceptionUtils.getStackTrace(ex)); //DON'T LOG THIS!!!!!
                }
            }
        }
    }

    public void close() {
    }

    public boolean requiresLayout() {
        return false;
    }

    public synchronized static void configure(@NonNull String currentProject, @NonNull String currentService) {
        if (!configured) {
            project = currentProject;
            service = currentService;
            instance = Long.toString(System.currentTimeMillis()); //this is basically the timestamp for when the app was last started
            configured = true;
        }
        else {
            throw new RuntimeException("Configure has already been called for this appender.");
        }
    }

    public static synchronized void reset(String appEnv) {
        if (!appEnv.equals(Utility.Deployment.UNIT_TEST.toString())) {
            throw new RuntimeException("Reset is only for testing.");
        }
        project = null;
        service = null;
        instance = null;
        configured = false;
    }
}