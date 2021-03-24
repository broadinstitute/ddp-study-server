package org.broadinstitute.dsm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.AbstractionActivity;
import org.broadinstitute.dsm.db.AbstractionField;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.model.AbstractionWrapper;
import org.broadinstitute.dsm.model.ParticipantWrapper;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RouteTestAbstraction extends TestHelper {

    @BeforeClass
    public static void first() throws Exception {
        setupDB();
        startDSMServer();
        startMockServer();
        setupMock();
        setupUtils();

        addTestParticipant(TEST_DDP, "ABSTRACTION_PARTICIPANT_ID", "FAKE_DDP_PHYSICIAN_ID");
        setupAbstractionForm();
    }

    @AfterClass
    public static void last() {
        stopMockServer();
        stopDSMServer();
        cleanDB();
        cleanupDB();
    }

    private static void setupMock() throws Exception {
        setupDDPRoutes();
    }

    private static void setupDDPRoutes() throws Exception {
        String messageParticipant = TestUtil.readFile("ddpResponses/ParticipantInstitutions.json");
        mockDDP.when(
                request().withPath("/ddp/participantinstitutions"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
    }

    //kits will get deleted even if test failed!
    private static void cleanDB() {
        DBTestUtil.deleteAbstractionData("ABSTRACTION_PARTICIPANT_ID");
        DBTestUtil.deleteAllParticipantData("ABSTRACTION_PARTICIPANT_ID", true);
        //delete the field added for a test
        DBTestUtil.executeQuery("DELETE FROM medical_record_abstraction_field WHERE ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\") AND display_name = 'New Field'");
        DBTestUtil.executeQuery("DELETE FROM medical_record_abstraction_field WHERE ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\") AND display_name = 'New Field in Group'");
        DBTestUtil.executeQuery("DELETE FROM medical_record_abstraction_group WHERE ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\") AND display_name = 'Unit Test Group New'");
    }

    private static void setupAbstractionForm() {
        //only add abstraction fields if TEST_DDP doesn't have them yet
        if (!DBTestUtil.checkIfValueExists("SELECT * from medical_record_abstraction_group where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?)", TEST_DDP)) {
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_group set ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\"), display_name=\"Unit Test Group 1\", order_number = 1");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text) values " +
                    "('DOB', 'date', (SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 1'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\"), 1, 'DOB - Help text')");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, possible_values, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text) values " +
                    "('Sex', 'button_select', '[{\"value\":\"female\"}, {\"value\": \"male\"}]', " +
                    "(SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 1'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\"), 2, 'Sex - Help text')");

            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_group set ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\"), display_name=\"Unit Test Group 2\", order_number = 2 ");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, possible_values, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text) values " +
                    "('Diagnostic Sample Type', 'select', '[{\"value\":\"valueI\"}, {\"value\": \"valueA\"}]', " +
                    "(SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 2'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name =  \"" + TEST_DDP + "\"), 1, 'Diagnostic Sample Type - Help text')");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text, possible_values) values " +
                    "('Medication', 'multi_type_array', (SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 2'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\"), 2, 'Medication - Help text'," +
                    "'[{\"value\":\"Name of Drug\",\"type\":\"drugs\",\"type2\":\"select\"},{\"value\":\"Start\",\"type\":\"date\"},{\"value\":\"Stop\",\"type\":\"date\"},{\"value\":\"Trail\",\"type\":\"checkbox\"},{\"value\":\"Name\",\"type\":\"text\"},{\"value\":\"Treatment Response\",\"type\":\"options\",\"values\":[{\"value\":\"stable\"},{\"value\":\"response\"},{\"value\":\"progression\"}]},{\"value\":\"DC Reason\",\"type\":\"options\",\"values\":[{\"value\":\"v1\"},{\"value\":\"v2\"},{\"value\":\"v3\"}]}]')");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, possible_values, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text) values " +
                    "('Kit Type', 'multi_select', '[{\"value\":\"valueI\"}, {\"value\": \"valueA\"}]', " +
                    "(SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 2'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name =  \"" + TEST_DDP + "\"), 3, 'Kit Type - Help text')");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text) values " +
                    "('Favorite number', 'number', " +
                    "(SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 2'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name =  \"" + TEST_DDP + "\"), 4, 'Favorite number - Help text')");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text, possible_values) values " +
                    "('Recurrence', 'multi_type_array', " +
                    "(SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 2'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name =  \"" + TEST_DDP + "\"), 5, 'Recurrence - Help text', " +
                    "'[{\"value\":\"Location\",\"type\":\"options\",\"values\":[{\"value\":\"Brain\"},{\"value\":\"Pinkie\"},{\"value\":\"Toe\"}]},{\"value\":\"Date\",\"type\":\"date\"}]')");

            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_group set ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\"), display_name=\"Unit Test Group 3\", order_number = 3 ");
            DBTestUtil.executeQuery("INSERT INTO medical_record_abstraction_field (display_name, type, medical_record_abstraction_group_id, ddp_instance_id, order_number, help_text) values " +
                    "('Text area', 'textarea', (SELECT medical_record_abstraction_group_id from medical_record_abstraction_group where display_name='Unit Test Group 3'), " +
                    "(SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\"), 1, 'Text area - Help text')");
        }
    }

    @Test
    public void getFormControls() throws Exception {
        //get abstraction fields and groups
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/abstractionformcontrols?realm=" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        Gson gson = new GsonBuilder().create();
        AbstractionGroup[] abstractionGroups = gson.fromJson(DDPRequestUtil.getContentAsString(response), AbstractionGroup[].class);

        //check if you get back what is in the db for the realm
        String groups = DBTestUtil.getQueryDetail("select count(*) from medical_record_abstraction_group where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?)", TEST_DDP, "count(*)");
        Assert.assertEquals(Integer.parseInt(groups), abstractionGroups.length);
        Assert.assertEquals("Unit Test Group 1", abstractionGroups[0].getDisplayName());
        List strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add("Unit Test Group 1");
        String fields = DBTestUtil.getStringFromQuery("select count(*) from medical_record_abstraction_field where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?) AND medical_record_abstraction_group_id = (select medical_record_abstraction_group_id from medical_record_abstraction_group where display_name = ?) ", strings, "count(*)");
        Assert.assertEquals(Integer.parseInt(fields), abstractionGroups[0].getFields().size());
        Assert.assertEquals("Unit Test Group 2", abstractionGroups[1].getDisplayName());
        strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add("Unit Test Group 2");
        fields = DBTestUtil.getStringFromQuery("select count(*) from medical_record_abstraction_field where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?) AND medical_record_abstraction_group_id = (select medical_record_abstraction_group_id from medical_record_abstraction_group where display_name = ?) ", strings, "count(*)");
        Assert.assertEquals(Integer.parseInt(fields), abstractionGroups[1].getFields().size());
        Assert.assertEquals("Unit Test Group 3", abstractionGroups[2].getDisplayName());
        strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add("Unit Test Group 3");
        fields = DBTestUtil.getStringFromQuery("select count(*) from medical_record_abstraction_field where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?) AND medical_record_abstraction_group_id = (select medical_record_abstraction_group_id from medical_record_abstraction_group where display_name = ?) ", strings, "count(*)");
        Assert.assertEquals(Integer.parseInt(fields), abstractionGroups[2].getFields().size());
    }

    @Test
    public void addGroup() throws Exception {
        String newGroup = "Unit Test Group New";

        //get numbers of fields in group before adding a new field
        List strings = new ArrayList<>();
        strings.add(TEST_DDP);
        String groups = DBTestUtil.getStringFromQuery("select count(*) from medical_record_abstraction_group where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?)", strings, "count(*)");

        //adding a new group
        String json = "[{\"newAdded\": true, \"displayName\": \"" + newGroup + "\"}]";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/abstractionformcontrols?realm=" + TEST_DDP), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //add a field to the group, so that the group is there for the next call to get the form (only groups with fields are returned)
        addField(newGroup, "New Field in Group", "text", 4);

        //getting fields for realm
        response = TestUtil.performGet(DSM_BASE_URL, "/ui/abstractionformcontrols?realm=" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        AbstractionGroup[] abstractionGroups = gson.fromJson(DDPRequestUtil.getContentAsString(response), AbstractionGroup[].class);
        String newGroupsCount = DBTestUtil.getStringFromQuery("select count(*) from medical_record_abstraction_group where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?)", strings, "count(*)");
        Assert.assertEquals(Integer.parseInt(newGroupsCount), abstractionGroups.length);

        //check that field count is actually now different than it was before
        Assert.assertNotEquals(groups, newGroupsCount);
    }

    @Test
    public void addField() throws Exception {
        addField("Unit Test Group 3", "New Field", "text", 3);
    }

    private void addField(@NonNull String groupName, @NonNull String fieldName, @NonNull String fieldType, int orderNumber) throws Exception {
        //get group id
        List strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(groupName);
        String abstractionGroupId = DBTestUtil.getStringFromQuery("SELECT * from medical_record_abstraction_group where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?) and display_name = ?", strings, "medical_record_abstraction_group_id");

        //get numbers of fields in group before adding a new field
        String fields = DBTestUtil.getStringFromQuery("select count(*) from medical_record_abstraction_field where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?) AND medical_record_abstraction_group_id = (select medical_record_abstraction_group_id from medical_record_abstraction_group where display_name = ?) ", strings, "count(*)");

        //adding a new field to the group
        String groupID = abstractionGroupId == null ? "null, \"newAdded\":true " : ("\"" + abstractionGroupId + "\"");
        String json = "[{\"abstractionGroupId\":" + groupID + ", \"displayName\": \"" + groupName + "\", \"orderNumber\": " + orderNumber + ", \"changed\": true, \"fields\": [{\"newAdded\":true, \"displayName\":\"" + fieldName + "\", \"helpText\":\"" + fieldName + " - Help Text\", \"type\":\"" + fieldType + "\"}]}]";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/abstractionformcontrols?realm=" + TEST_DDP), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //getting fields for realm
        response = TestUtil.performGet(DSM_BASE_URL, "/ui/abstractionformcontrols?realm=" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        AbstractionGroup[] abstractionGroups = gson.fromJson(DDPRequestUtil.getContentAsString(response), AbstractionGroup[].class);

        //check number of fields is same as in db
        boolean found = false;
        String newFieldsCount = DBTestUtil.getStringFromQuery("select count(*) from medical_record_abstraction_field where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?) AND medical_record_abstraction_group_id = (select medical_record_abstraction_group_id from medical_record_abstraction_group where display_name = ?) ", strings, "count(*)");
        for (AbstractionGroup group : abstractionGroups) {
            if (group.getDisplayName().equals(groupName)) {
                Assert.assertEquals(Integer.parseInt(newFieldsCount), group.getFields().size());
                found = true;
                break;
            }
        }

        if (!found) {
            Assert.fail("Group was not in the response");
        }
        //check that field count is actually now different than it was before
        Assert.assertNotEquals(fields, newFieldsCount);
    }

    @Test
    public void abstraction() throws Exception {
        abstraction("abstraction", "{\\\"dateString\\\":\\\"1966-03-25\\\",\\\"est\\\":true}", "{\"dateString\":\"1966-03-25\",\"est\":true}");
        abstraction("review", "{\\\"dateString\\\":\\\"1966-03-02\\\",\\\"est\\\":true}", "{\"dateString\":\"1966-03-02\",\"est\":true}");
        //TODO start qc now
    }

    public void abstraction(@NonNull String activityName, @NonNull String abstractionUniqueValue, @NonNull String abstractionUniqueValue2) throws Exception {
        String ddpParticipantId = "ABSTRACTION_PARTICIPANT_ID";
        ParticipantWrapper[] participants = RouteTest.getParticipants("/ui/applyFilter?parent=participantList&userId=26&realm=" + TEST_DDP);
        for (ParticipantWrapper participant : participants) {
            if (participant.getParticipant().getDdpParticipantId().equals(ddpParticipantId)) {
                List<AbstractionActivity> abstractionActivities = participant.getAbstractionActivities();
                if (abstractionActivities != null) {
                    for (AbstractionActivity activity : abstractionActivities) {
                        if (activityName.equals(activity.getActivity())) {
                            //TODO what now?
                        }
                        //activity is not yet started
                        Assert.assertNull(activity);
                    }
                    break;
                }
            }
        }
        //start abstraction
        changeAbstractionStatus(ddpParticipantId, TEST_DDP, activityName, "in_progress", "not_started", false);

        //get empty abstraction form
        AbstractionWrapper abstractionWrapper = getAbstractionFields(ddpParticipantId, TEST_DDP);
        List strings = new ArrayList<>();
        strings.add(TEST_DDP);
        String fieldCount = DBTestUtil.getStringFromQuery("SELECT count(*) FROM medical_record_abstraction_field WHERE ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?)", strings, "count(*)");

        //field count is the same no matter which abstraction activity it is
        Assert.assertEquals(abstractionWrapper.getAbstraction().size(), abstractionWrapper.getReview().size());
        Assert.assertEquals(abstractionWrapper.getReview().size(), abstractionWrapper.getQc().size());
        int count = 0;
        for (AbstractionGroup group : abstractionWrapper.getAbstraction()) {
            count += group.getFields().size();
            for (AbstractionGroup rGroup : abstractionWrapper.getReview()) {
                if (rGroup.getDisplayName().equals(group.getDisplayName())) {
                    Assert.assertEquals(rGroup.getFields().size(), group.getFields().size());
                    break;
                }
            }
            for (AbstractionGroup qGroup : abstractionWrapper.getQc()) {
                if (qGroup.getDisplayName().equals(group.getDisplayName())) {
                    Assert.assertEquals(qGroup.getFields().size(), group.getFields().size());
                    break;
                }
            }
        }
        Assert.assertEquals(Integer.parseInt(fieldCount), count);

        //change a field
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant(ddpParticipantId);
        strings = new ArrayList<>();
        strings.add("DOB");
        strings.add(TEST_DDP);
        String fieldId = DBTestUtil.getStringFromQuery("SELECT * FROM medical_record_abstraction_field WHERE display_name = ? AND ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?)", strings, "medical_record_abstraction_field_id");
        changeValue(null, participantId, activityName + "_value", abstractionUniqueValue, "value", "participantId", "me", "medical_record_" + activityName + "_id", "ddp_medical_record_" + activityName, fieldId);

        //change medication field
        String medicationValue = "[{\"Name of Drug\":null,\"Start \":\"{\\\"dateString\\\":\\\"2019-10-01\\\",\\\"est\\\":false}\",\"Stop\":null,\"Trail\":null,\"Name\":null,\"Treatment Response\":\"other\",\"DC Reason\":null,\"other\":[{\"Treatment Response\":\"some little response\"}]},{\"Name of Drug\":null,\"Start \":\"{\\\"dateString\\\":\\\"2019-12-03\\\",\\\"est\\\":false}\",\"Stop\":null,\"Trail\":null,\"Name\":null,\"Treatment Response\":null,\"DC Reason\":null}]";
        strings = new ArrayList<>();
        strings.add("Medication");
        strings.add(TEST_DDP);
        String medicationFieldId = DBTestUtil.getStringFromQuery("SELECT * FROM medical_record_abstraction_field WHERE display_name = ? AND ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?)", strings, "medical_record_abstraction_field_id");
        changeValue(null, participantId, activityName + "_value", medicationValue, "value", "participantId", "me", "medical_record_" + activityName + "_id", "ddp_medical_record_" + activityName, medicationFieldId);

        //get abstraction form again
        AbstractionWrapper abstractionWrapperChanged = getAbstractionFields(ddpParticipantId, TEST_DDP);

        //check that filled out values is not changing the abstraction size...
        Assert.assertEquals(abstractionWrapper.getAbstraction().size(), abstractionWrapperChanged.getAbstraction().size());
        checkAbstractionValue(abstractionWrapperChanged, "Unit Test Group 1", "DOB", abstractionUniqueValue2, activityName);

        //try to submit abstraction
        changeAbstractionStatus(ddpParticipantId, TEST_DDP, activityName, "submit", "in_progress", true);

        Collection<AbstractionGroup> abstractionGroups = abstractionWrapperChanged.getAbstraction();
        for (AbstractionGroup group : abstractionGroups) {
            for (AbstractionField field : group.getFields()) {
                if (!field.getDisplayName().equals("DOB") && !field.getDisplayName().equals("Medication")) {//or do it with id
                    //now set all fields to no_data = true
                    strings = new ArrayList<>();
                    strings.add(field.getDisplayName());
                    strings.add(TEST_DDP);
                    fieldId = DBTestUtil.getStringFromQuery("SELECT * FROM medical_record_abstraction_field WHERE display_name = ? AND ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?)", strings, "medical_record_abstraction_field_id");
                    changeValue(null, participantId, activityName + "_noData", 1, "value", "participantId", "me", "medical_record_" + activityName + "_id", "ddp_medical_record_" + activityName, fieldId);
                }
            }
        }
        //try to submit abstraction
        changeAbstractionStatus(ddpParticipantId, TEST_DDP, activityName, "submit", "in_progress", false);

        //check if multi_type_array got reordered by date
        strings = new ArrayList<>();
        strings.add(participantId);
        strings.add(medicationFieldId);
        String medicationValueAfterSubmit = DBTestUtil.getStringFromQuery("SELECT * FROM ddp_medical_record_" + activityName + " WHERE participant_id = ? AND medical_record_abstraction_field_id = ?", strings, "value");
        Assert.assertNotEquals(medicationValue, medicationValueAfterSubmit);
    }

    private void checkAbstractionValue(@NonNull AbstractionWrapper abstractionWrapperChanged, @NonNull String groupName, @NonNull String fieldName, @NonNull String value, @NonNull String activityName) throws Exception {
        boolean found = false;
        Collection<AbstractionGroup> activity = null;
        if (activityName.equals("abstraction")) {
            activity = abstractionWrapperChanged.getAbstraction();
        }
        else if (activityName.equals("review")) {
            activity = abstractionWrapperChanged.getReview();
        }
        else if (activityName.equals("qc")) {
            activity = abstractionWrapperChanged.getQc();
        }
        else {
            Assert.fail("Couldn't match activity name to activity");
        }
        if (activity != null) {
            for (AbstractionGroup group : activity) {
                if (group.getDisplayName().equals(groupName)) {
                    List<AbstractionField> fields = group.getFields();
                    for (AbstractionField field : fields) {
                        if (field.getDisplayName().equals(fieldName)) {
                            Assert.assertEquals(value, field.getFieldValue().getValue());
                            found = true;
                        }
                    }
                    break;
                }
            }
            if (!found) {
                Assert.fail("Value was not as changed!");
            }
        }
    }

    private void changeAbstractionStatus(@NonNull String ddpParticipantId, @NonNull String realm, @NonNull String activity, @NonNull String newStatus,
                                         @NonNull String currentStatus, boolean submitFail) throws Exception {
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant(ddpParticipantId);
        List strings = new ArrayList<>();
        strings.add(participantId);
        strings.add(activity);
        String activityId = DBTestUtil.getStringFromQuery("SELECT * FROM ddp_medical_record_abstraction_activities WHERE participant_id = ? AND activity = ?", strings, "medical_record_abstraction_activities_id");
        String json = "{\"ddpParticipantId\":\"" + ddpParticipantId + "\", \"realm\": \"" + realm + "\", \"status\": \"" + newStatus + "\", \"userId\": \"1\", \"abstraction\": {\"participantId\":\"" + participantId + "\",\"user\":\"Name of user\", \"activity\":\"" + activity + "\", \"aStatus\":\"" + currentStatus + "\"}}";

        if (activityId != null) {
            json = "{\"ddpParticipantId\":\"" + ddpParticipantId + "\", \"realm\": \"" + realm + "\", \"status\": \"" + newStatus + "\", \"userId\": \"1\", \"abstraction\": {\"participantId\":\"" + participantId + "\", \"medicalRecordAbstractionActivityId\":\"" + activityId + "\",\"user\":\"Name of user\", \"activity\":\"" + activity + "\", \"aStatus\":\"" + currentStatus + "\"}}";

        }
        //change abstraction aStatus
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/abstraction"), json, testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
        if (submitFail) {
            String message = DDPRequestUtil.getContentAsString(response);
            Result result = new Gson().fromJson(message, Result.class);
            Assert.assertEquals(500, result.getCode());
            Assert.assertEquals("Abstraction not complete", result.getBody());

        }
        else {
            String message = DDPRequestUtil.getContentAsString(response);
            Result result = new Gson().fromJson(message, Result.class);

            AbstractionActivity abstractionActivity = new Gson().fromJson(result.getBody(), AbstractionActivity.class);
            Assert.assertNotNull(abstractionActivity);
            Assert.assertNotNull(abstractionActivity.getMedicalRecordAbstractionActivityId());
        }
    }

    private AbstractionWrapper getAbstractionFields(@NonNull String ddpParticipantId, @NonNull String realm) throws Exception {
        //get abstraction values
        String json = "{\"ddpParticipantId\":\"" + ddpParticipantId + "\", \"realm\": \"" + realm + "\"}";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/abstraction"), json, testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        AbstractionWrapper abstractionWrapper = new GsonBuilder().create().fromJson(DDPRequestUtil.getContentAsString(response), AbstractionWrapper.class);
        Assert.assertNotNull(abstractionWrapper);
        return abstractionWrapper;
    }

    //TODO add missing tests
    // unlock a participant
    // get a qc of a participant
    // submit a qc for a participant
    // medication/recurrence comparison

}
