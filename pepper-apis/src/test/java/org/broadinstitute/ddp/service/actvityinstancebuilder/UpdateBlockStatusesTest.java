package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.CONTROL_BLOCK_GUID;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.TOGGLED_BLOC_GUID;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createEmptyTestInstance;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createTestInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreatorHelper;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UpdateBlockStatusesTest extends TxnAwareBaseTest {

    private static String userGuid = TestConstants.TEST_USER_GUID;
    private static PexInterpreter interpreter;
    private static FormInstanceCreatorHelper formInstanceCreatorHelper;

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private Handle mockHandle;

    @BeforeClass
    public static void setup() {
        interpreter = new TreeWalkInterpreter();
        formInstanceCreatorHelper = new FormInstanceCreatorHelper();
    }

    @Before
    public void setupMocks() {
        mockHandle = mock(Handle.class);
        var mockUserDao = mock(UserDao.class);
        var mockProfleDao = mock(UserProfileDao.class);
        var mockActInstDao = mock(ActivityInstanceDao.class);
        doReturn(mockUserDao).when(mockHandle).attach(UserDao.class);
        doReturn(mockProfleDao).when(mockHandle).attach(UserProfileDao.class);
        doReturn(mockActInstDao).when(mockHandle).attach(ActivityInstanceDao.class);
        doReturn(Optional.empty()).when(mockUserDao).findUserByGuid(anyString());
        doReturn(Optional.empty()).when(mockProfleDao).findProfileByUserId(anyLong());
        doNothing().when(mockActInstDao).saveSubstitutions(anyLong(), any());
    }

    @Test
    public void testUpdateBlockStatuses_nonToggleableBlocksUntouched() {
        TransactionWrapper.useTxn(handle -> {
            ContentBlock content = new ContentBlock(1);
            FormSection s1 = new FormSection(Collections.singletonList(content));
            FormInstance form = createEmptyTestInstance();
            form.addBodySections(Collections.singletonList(s1));

            content.setShown(false);
            content.setEnabled(false);
            formInstanceCreatorHelper.updateBlockStatuses(handle, form, interpreter, userGuid, userGuid, form.getGuid(), null);
            assertFalse(content.isShown());
            assertFalse(content.isEnabled());
            content.setEnabledExpr("true");
            formInstanceCreatorHelper.updateBlockStatuses(handle, form, interpreter, userGuid, userGuid, form.getGuid(), null);
            assertFalse(content.isShown());
            assertTrue(content.isEnabled());
            content.setShownExpr("true");
            formInstanceCreatorHelper.updateBlockStatuses(handle, form, interpreter, userGuid, userGuid, form.getGuid(), null);
            assertTrue(content.isShown());
            assertTrue(content.isEnabled());
        });
    }

    @Test
    public void testUpdateBlockStatuses_withoutExpr_defaultShown() {
        TransactionWrapper.useTxn(handle -> {
            FormInstance form = createTestInstance();
            formInstanceCreatorHelper.updateBlockStatuses(handle, form, interpreter, userGuid, userGuid, form.getGuid(), null);

            for (FormSection sect : form.getBodySections()) {
                for (FormBlock block : sect.getBlocks()) {
                    if (CONTROL_BLOCK_GUID.equals(block.getGuid())) {
                        assertTrue(block.isShown());
                    }
                }
            }
        });
    }

    @Test
    public void testUpdateBlockStatuses_withExpr_evaluated() {
        PexInterpreter mockInterpreter = mock(PexInterpreter.class);

        TransactionWrapper.useTxn(handle -> {
            FormInstance form = createTestInstance();

            when(mockInterpreter.eval(anyString(), any(Handle.class), eq(userGuid), eq(userGuid), eq(form.getGuid()), any()))
                    .thenReturn(true);

            formInstanceCreatorHelper.updateBlockStatuses(handle, form, mockInterpreter, userGuid, userGuid, form.getGuid(), null);

            for (FormSection sect : form.getBodySections()) {
                for (FormBlock block : sect.getBlocks()) {
                    if (TOGGLED_BLOC_GUID.equals(block.getGuid())) {
                        assertTrue(block.isShown());
                    }
                }
            }
        });
    }

    @Test
    public void testUpdatedBlockStatuses_withExpr_evalError() {
        thrown.expect(DDPException.class);
        thrown.expectMessage("pex shown expression");

        PexInterpreter mockInterpreter = mock(PexInterpreter.class);

        TransactionWrapper.useTxn(handle -> {
            FormInstance form = createTestInstance();

            when(mockInterpreter.eval(anyString(), any(Handle.class), eq(userGuid), eq(userGuid), eq(form.getGuid()), any()))
                    .thenThrow(new PexException("testing"));

            formInstanceCreatorHelper.updateBlockStatuses(handle, form, mockInterpreter, userGuid, userGuid, form.getGuid(), null);
            fail("expected exception was not thrown");
        });
    }
}
