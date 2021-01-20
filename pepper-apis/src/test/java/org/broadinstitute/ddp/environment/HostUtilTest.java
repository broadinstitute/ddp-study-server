package org.broadinstitute.ddp.environment;

import static org.broadinstitute.ddp.environment.HostUtil.APPENGINE_INSTANCE_ENV_VAR;
import static org.broadinstitute.ddp.environment.HostUtil.APPENGINE_SERVICE_ENV_VAR;
import static org.broadinstitute.ddp.environment.HostUtil.FAKE_GAE_SERVICE_TITLE;
import static org.broadinstitute.ddp.environment.HostUtil.FAKE_VALUE_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;


public class HostUtilTest {

    // should be set in pom.xml
    private static final String GAE_INSTANCE_ID = "test-GAE-instance";
    private static final String GAE_SERVICE_ID = "test-GAE-service";

    @Test
    public void testHostAndServiceNamesForGAE() {
        HostUtil.resetValues();
        assertEquals(GAE_INSTANCE_ID, HostUtil.getGAEInstanceOrHostName());
        assertEquals(GAE_SERVICE_ID, HostUtil.getGAEServiceName());
    }

    /**
     * Test the case:
     * <pre>
     * GAE_INSTANCE not set
     * GAE_SERVICE not set
     * HOST = "mocked_hostname".
     *
     * EXPECTED that:
     * getGAEInstanceOrHostName() = "mocked_hostname";
     * getGAEServiceName() = "unknown-GAE-service-NLGHL" (where last 5 chars - randomly generated);
     * </pre>
     */
    @Test
    public void testHostAndServiceNamesForGAEWhenGAEEnvVarsAreNotSet() {
        HostUtil.resetValues();
        final String MOCKED_HOST_NAME = "mocked_hostname";
        try (MockedStatic<SystemUtil> dummySystem = Mockito.mockStatic(SystemUtil.class)) {
            dummySystem.when(() -> SystemUtil.getEnv(APPENGINE_INSTANCE_ENV_VAR)).thenReturn(null);
            dummySystem.when(() -> SystemUtil.getEnv(APPENGINE_SERVICE_ENV_VAR)).thenReturn(null);
            dummySystem.when(() -> SystemUtil.getLocalHostName()).thenReturn(MOCKED_HOST_NAME);

            assertEquals(MOCKED_HOST_NAME, HostUtil.getGAEInstanceOrHostName());
            assertTrue(HostUtil.getGAEServiceName().startsWith(
                    FAKE_VALUE_PREFIX + FAKE_GAE_SERVICE_TITLE));
        }
    }

    /**
     * Test the case:
     * <pre>
     * GAE_INSTANCE is set
     * GAE_SERVICE is set
     *
     * EXPECTED that:
     * getGAEInstanceOrHostName() = "mocked_GAE_instance";
     * getGAEServiceName() = "mocked_GAE_service" ;
     * </pre>
     */
    @Test
    public void testHostAndServiceNamesForGAEWhenGAEEnvVarsAreSet() {
        HostUtil.resetValues();
        final String MOCKED_GAE_INSTANCE = "mocked_GAE_instance";
        final String MOCKED_GAE_SERVICE = "mocked_GAE_service";
        try (MockedStatic<SystemUtil> dummySystem = Mockito.mockStatic(SystemUtil.class)) {
            dummySystem.when(() -> SystemUtil.getEnv(APPENGINE_INSTANCE_ENV_VAR)).thenReturn(MOCKED_GAE_INSTANCE);
            dummySystem.when(() -> SystemUtil.getEnv(APPENGINE_SERVICE_ENV_VAR)).thenReturn(MOCKED_GAE_SERVICE);

            assertEquals(MOCKED_GAE_INSTANCE, HostUtil.getGAEInstanceOrHostName());
            assertEquals(MOCKED_GAE_SERVICE, HostUtil.getGAEServiceName());
        }
    }
}
