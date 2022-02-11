package org.broadinstitute.dsm.model.participant.data;

import static org.broadinstitute.dsm.TestHelper.setupDB;

import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class AddFamilyMemberPayloadTest {

    private static BookmarkDao bookmarkDao;
    private static int createdTestFamilyIdPK;

    @BeforeClass
    public static void setUp() {
        setupDB();
        bookmarkDao = new BookmarkDao();
        createdTestFamilyIdPK = bookmarkDao.create(new BookmarkDto.Builder(1000, "test_family_id").build());
    }

    @AfterClass
    public static void tearDown() {
        if (createdTestFamilyIdPK > 0) bookmarkDao.delete(createdTestFamilyIdPK);
    }

    @Test
    public void testGenerateCollaboratorParticipantId() {
        FamilyMemberDetails familyMemberDetails = new FamilyMemberDetails();
        familyMemberDetails.setFamilyId(2000);
        familyMemberDetails.setSubjectId("3");
        AddFamilyMemberPayload familyMemberPayload = new AddFamilyMemberPayload.Builder("", "")
                .withData(familyMemberDetails)
                .build();
        String collaboratorParticipantId = familyMemberPayload.generateCollaboratorParticipantId();
        Assert.assertEquals("RGP_2000_3", collaboratorParticipantId);
    }

    @Test
    public void testGetOrGenerateFamilyId() {
        AddFamilyMemberPayload familyMemberPayload = new AddFamilyMemberPayload.Builder("", "test")
                .build();
        long familyId = familyMemberPayload.getOrGenerateFamilyId();
        Assert.assertTrue(familyId > 0);
    }
}