package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.json.DeployedAppVersionResponse;
import org.broadinstitute.ddp.util.MiscUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class GetDeployedAppVersionRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetDeployedAppVersionRoute.class);

    @Override
    public DeployedAppVersionResponse handle(Request request, Response response) throws Exception {
        Package pkg = GetDeployedAppVersionRoute.class.getPackage();
        String backendVersion = pkg.getImplementationVersion();
        String appSHA = MiscUtil.calculateSHA1(
                MiscUtil.getJarFileForClass(GetDeployedAppVersionRoute.class)
        );
        return new DeployedAppVersionResponse(backendVersion, appSHA);
    }

}
