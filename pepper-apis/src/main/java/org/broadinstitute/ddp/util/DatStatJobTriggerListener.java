package org.broadinstitute.ddp.util;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.NumberGauge;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ebaker on 5/25/16.
 */
public class DatStatJobTriggerListener extends BasicTriggerListener
{
    private static final AtomicInteger jobHealthy = new AtomicInteger(0);
    private static final NumberGauge jobHealthyGauge = new NumberGauge(MonitorConfig.builder("datstat_job_ok_gauge").build(), jobHealthy);
    //explicitly wire up the metrics using a static initializer
    static {
        DefaultMonitorRegistry.getInstance().register(jobHealthyGauge);
    }

    @Override
    public String getName() {
        return "DATSTAT_JOB_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {
        if (veto) {
            jobHealthy.set(0); //unable to start
        }
        else
        {
            jobHealthy.set(1); //able to start
        }
    }
}
