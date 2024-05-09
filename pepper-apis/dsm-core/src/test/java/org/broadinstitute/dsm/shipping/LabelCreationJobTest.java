package org.broadinstitute.dsm.shipping;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.jobs.LabelCreationJob;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class LabelCreationJobTest extends DbAndElasticBaseTest {
    @Mock
    EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);
    @Mock
    Address mockEasyPostAddress = mock(Address.class);
    @Mock
    private JobExecutionContext context;
    @Mock
    private KitRequestCreateLabel kitRequestCreateLabelMock;

    private static final String dsmKitId = "TestKitId";



    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(kitRequestCreateLabelMock.getInstanceName()).thenReturn("TestInstance");
        when(kitRequestCreateLabelMock.getDdpParticipantId()).thenReturn("TestParticipantId");
        when(kitRequestCreateLabelMock.getDsmKitRequestId()).thenReturn("TestKitRequestId");

    }

    @Test
    public void testExecute_NoLabelsNeeded() throws JobExecutionException {
        try (MockedStatic<DBUtil> mockedDbUtil = Mockito.mockStatic(DBUtil.class)) {
            mockedDbUtil.when(() -> DBUtil.getBookmark(KitUtil.BOOKMARK_LABEL_CREATION_RUNNING)).thenReturn(0L);
            try (MockedStatic<KitUtil> mockedKitUtil = Mockito.mockStatic(KitUtil.class)) {
                mockedKitUtil.when(KitUtil::getListOfKitsLabelTriggered).thenReturn(Collections.emptyList());
                new LabelCreationJob().execute(context);
                mockedKitUtil.verify(() -> KitUtil.createLabel(any(), any()), times(0));
            }
            mockedDbUtil.verify(() -> DBUtil.updateBookmark(0, KitUtil.BOOKMARK_LABEL_CREATION_RUNNING), times(0));
        }

    }

    @Test
    public void testExecute_LongRunningLabelCreation() throws JobExecutionException {
        try (MockedStatic<DBUtil> mockedDbUtil = Mockito.mockStatic(DBUtil.class)) {
            mockedDbUtil.when(() -> DBUtil.getBookmark(KitUtil.BOOKMARK_LABEL_CREATION_RUNNING)).thenReturn(System.currentTimeMillis()
                    - SystemUtil.MILLIS_PER_HOUR - 10);
            try (MockedStatic<KitUtil> mockedKitUtil = Mockito.mockStatic(KitUtil.class)) {
                mockedKitUtil.when(KitUtil::getListOfKitsLabelTriggered).thenReturn(Collections.emptyList());
                new LabelCreationJob().execute(context);
                mockedKitUtil.verify(() -> KitUtil.createLabel(any(), any()), times(0));
            }
            //make sure the bookmark is reset when the job has been running for over an hour
            mockedDbUtil.verify(() -> DBUtil.updateBookmark(0, KitUtil.BOOKMARK_LABEL_CREATION_RUNNING), times(1));
        }
    }

    @Test
    public void testErrorMessage_ResearchKitInClinicalStudy() {
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().withResearchProject("RESEARCH").build();
        when(mockEasyPostAddress.getCountry()).thenReturn("CA");
        when(mockEasyPostAddress.getState()).thenReturn("ON");
        String oldErrorMessage = "Some error message";
        String newErrorMessage = KitUtil.checkResearchKitInClinicalStudies(oldErrorMessage, ddpInstanceDto, mockEasyPostAddress, dsmKitId);
        assertEquals(KitUtil.PECGS_RESEARCH + " " + oldErrorMessage, newErrorMessage);

        when(mockEasyPostAddress.getCountry()).thenReturn("US");
        when(mockEasyPostAddress.getState()).thenReturn("NY");
        newErrorMessage = KitUtil.checkResearchKitInClinicalStudies(oldErrorMessage, ddpInstanceDto, mockEasyPostAddress, dsmKitId);
        assertEquals(KitUtil.PECGS_RESEARCH + " " + oldErrorMessage, newErrorMessage);

        when(mockEasyPostAddress.getCountry()).thenReturn("US");
        when(mockEasyPostAddress.getState()).thenReturn("MA");
        newErrorMessage = KitUtil.checkResearchKitInClinicalStudies(oldErrorMessage, ddpInstanceDto, mockEasyPostAddress, dsmKitId);
        assertEquals(oldErrorMessage, newErrorMessage);

        when(mockEasyPostAddress.getCountry()).thenReturn("US");
        when(mockEasyPostAddress.getState()).thenReturn(null);
        newErrorMessage = KitUtil.checkResearchKitInClinicalStudies(oldErrorMessage, ddpInstanceDto, mockEasyPostAddress, dsmKitId);
        assertEquals(oldErrorMessage, newErrorMessage);

        oldErrorMessage = "";
        when(mockEasyPostAddress.getCountry()).thenReturn("US");
        when(mockEasyPostAddress.getState()).thenReturn("NY");
        newErrorMessage = KitUtil.checkResearchKitInClinicalStudies(oldErrorMessage, ddpInstanceDto, mockEasyPostAddress, dsmKitId);
        assertEquals(KitUtil.PECGS_RESEARCH, newErrorMessage);

        ddpInstanceDto = new DDPInstanceDto.Builder().withResearchProject(null).build();
        when(mockEasyPostAddress.getCountry()).thenReturn("US");
        when(mockEasyPostAddress.getState()).thenReturn("NY");
        newErrorMessage = KitUtil.checkResearchKitInClinicalStudies(oldErrorMessage, ddpInstanceDto, mockEasyPostAddress, dsmKitId);
        assertEquals(oldErrorMessage, newErrorMessage);

    }

    @Test
    public void testCreateLabel_WithEasyPostUtilCreationException() throws JobExecutionException {
        try (MockedStatic<DBUtil> mockedDbUtil = Mockito.mockStatic(DBUtil.class)) {
            mockedDbUtil.when(() -> DBUtil.getBookmark(KitUtil.BOOKMARK_LABEL_CREATION_RUNNING)).thenReturn(0L);
            mockedDbUtil.when(() -> DBUtil.updateBookmark(ArgumentMatchers.anyLong(), anyString())).thenCallRealMethod();
            try (MockedStatic<KitUtil> mockedKitUtil = Mockito.mockStatic(KitUtil.class)) {
                List<KitRequestCreateLabel> kits = Arrays.asList(kitRequestCreateLabelMock);
                mockedKitUtil.when(KitUtil::getListOfKitsLabelTriggered).thenReturn(kits);
                mockedKitUtil.when(() -> KitUtil.createLabel(any(), any())).thenCallRealMethod();
                new LabelCreationJob().execute(context);
            }
            // Verify bookmark reset
            mockedDbUtil.verify(() -> DBUtil.updateBookmark(0, KitUtil.BOOKMARK_LABEL_CREATION_RUNNING), times(1));
        }
    }

    @Test
    public void testCreateLabel_WithNpeException() {
        try (MockedStatic<DBUtil> mockedDbUtil = Mockito.mockStatic(DBUtil.class)) {
            try (MockedStatic<EasyPostUtil> mockStaticEasyPostUtil = Mockito.mockStatic(EasyPostUtil.class)) {
                mockStaticEasyPostUtil.when(() -> EasyPostUtil.fromInstanceName(anyString())).thenReturn(mockEasyPostUtil);
                when(kitRequestCreateLabelMock.getAddressIdTo()).thenReturn("ValidAddressId");
                // Simulate exception during shipment purchase
                doThrow(new RuntimeException("Shipment Purchase Exception"))
                        .when(mockEasyPostUtil).buyShipment(anyString(), anyString(), anyString(), any(), any(), any(), anyString(), any());
            } catch (EasyPostException e) {
                e.printStackTrace();
                Assert.fail("Unexpected exception");
            }

            List<KitRequestCreateLabel> kits = Arrays.asList(kitRequestCreateLabelMock);
            KitUtil.createLabel(kits, mockEasyPostUtil);
            mockedDbUtil.verify(() -> DBUtil.updateBookmark(0, KitUtil.BOOKMARK_LABEL_CREATION_RUNNING), times(1));
        }

    }

    @Test
    public void testCreateLabel_WithShipmentPurchaseException() {
        try (MockedStatic<DBUtil> mockedDbUtil = Mockito.mockStatic(DBUtil.class)) {
            mockedDbUtil.when(() -> DBUtil.getBookmark(KitUtil.BOOKMARK_LABEL_CREATION_RUNNING)).thenReturn(0L);
            mockedDbUtil.when(() -> DBUtil.updateBookmark(ArgumentMatchers.anyLong(), anyString())).thenCallRealMethod();
            try (MockedStatic<EasyPostUtil> mockStaticEasyPostUtil = Mockito.mockStatic(EasyPostUtil.class)) {
                mockStaticEasyPostUtil.when(() -> EasyPostUtil.fromInstanceName(anyString())).thenReturn(mockEasyPostUtil);
                try (MockedStatic<DDPInstance> mockStaticDdpInstance = Mockito.mockStatic(DDPInstance.class)) {
                    mockStaticDdpInstance.when(() -> DDPInstance.getDDPInstance(anyString())).thenReturn(new DDPInstance(1,
                            "TestInstance"));
                    try (MockedStatic<KitRequestShipping> mockedKitRequestShipping = Mockito.mockStatic(KitRequestShipping.class)) {
                        mockedKitRequestShipping.when(() -> KitRequestShipping.getToAddressId(any(), any(), anyString(), any(), any()))
                                .thenReturn(mockEasyPostAddress);
                        when(mockEasyPostAddress.getId()).thenReturn("someAddressId");
                        try (MockedStatic<KitUtil> mockedKitUtil = Mockito.mockStatic(KitUtil.class)) {
                            when(kitRequestCreateLabelMock.getAddressIdTo()).thenReturn("someAddressId");
                            when(kitRequestCreateLabelMock.getParticipantCollaboratorId()).thenReturn("collaboratorParticipantId");
                            List<KitRequestCreateLabel> kits = Arrays.asList(kitRequestCreateLabelMock);
                            mockedKitUtil.when(KitUtil::getListOfKitsLabelTriggered).thenReturn(kits);
                            mockedKitUtil.when(() -> KitUtil.getDDPParticipant(anyString(), any())).thenReturn(new DDPParticipant());
                            // Simulate exception during shipment purchase
                            try {
                                doThrow(new RuntimeException("Shipment Purchase Exception"))
                                        .when(mockEasyPostUtil).buyShipment(anyString(), anyString(), anyString(), any(), any(), any(),
                                                anyString(), any());
                                mockedKitUtil.when(() -> KitUtil.createLabel(any(), any())).thenCallRealMethod();
                                new LabelCreationJob().execute(context);
                                mockedDbUtil.verify(() -> DBUtil.updateBookmark(0, KitUtil.BOOKMARK_LABEL_CREATION_RUNNING),
                                        times(1));
                            } catch (EasyPostException e) {
                                e.printStackTrace();
                                Assert.fail("Unexpected exception");
                            } catch (JobExecutionException e) {
                                e.printStackTrace();
                                Assert.fail("Unexpected exception");
                            }
                        }
                    }
                }
            }
        }

    }

}
