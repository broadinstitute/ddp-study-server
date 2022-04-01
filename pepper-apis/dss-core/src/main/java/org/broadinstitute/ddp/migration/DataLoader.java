package org.broadinstitute.ddp.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.jdbi.v3.core.Handle;

@Slf4j
class DataLoader {
    private static final String WITHDREW_REASON_CODE = "WITHDREW";

    private final Config cfg;
    private final boolean isProdRun;
    private final String studyGuid;
    private final FileReader fileReader;
    private final Gson gson;
    private final Mapping mapping;
    private final UserLoader userLoader;
    private final DsmDataLoader dsmLoader;
    private final Map<String, Long> activityCodeToId = new HashMap<>();
    private final Map<Long, ActivityVersionDto> activityIdToLatestVersion = new HashMap<>();
    private final Map<String, String> familyIdToParticipantAltPid = new HashMap<>();

    DataLoader(Config cfg, FileReader fileReader, boolean isProdRun) {
        this.cfg = cfg;
        this.isProdRun = isProdRun;
        this.studyGuid = cfg.getString(LoaderConfigFile.STUDY_GUID);
        this.fileReader = fileReader;
        this.gson = new Gson();
        this.mapping = initMappingFile();
        this.userLoader = new UserLoader(cfg);
        this.dsmLoader = new DsmDataLoader();
    }

    private Mapping initMappingFile() {
        Path path = Path.of(cfg.getString(LoaderConfigFile.MAPPING_FILE));
        Mapping mapping;
        try {
            var reader = Files.newBufferedReader(path);
            mapping = gson.fromJson(reader, Mapping.class);
        } catch (IOException e) {
            throw new LoaderException(e);
        }
        if (!studyGuid.equals(mapping.getStudyGuid())) {
            throw new LoaderException("Mapping file study guid does not match!");
        }
        log.info("Using mapping file: {}", path);
        return mapping;
    }

    public void processMailingListFiles() {
        log.info("");

        Set<String> filenames = fileReader.listMailingListFiles();
        log.info("Found {} mailing list files", filenames.size());

        for (var filename : filenames) {
            log.info("Working on mailing list file: {}", filename);
            var data = gson.fromJson(fileReader.readContent(filename), MailingListFile.class);
            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS,
                    handle -> loadMailingListContacts(handle, data.getContacts()));
        }
    }

    private void loadMailingListContacts(Handle handle, List<MailingListContact> contacts) {
        String padding = "  ";
        log.info(padding + "Mailing list: loading {} contacts", contacts.size());

        List<JdbiMailingList.MailingListEntryDto> entryDtos = new ArrayList<>();
        for (var contact : contacts) {
            String firstName = StringUtils.defaultIfBlank(contact.getFirstName(), "");
            String lastName = StringUtils.defaultIfBlank(contact.getLastName(), "");
            entryDtos.add(new JdbiMailingList.MailingListEntryDto(
                    firstName, lastName, contact.getEmail(),
                    studyGuid, null, contact.getInfo(),
                    contact.getDateCreatedMillis(), null, null));
        }

        int[] counts = handle.attach(JdbiMailingList.class).bulkInsertIfNotStoredAlready(entryDtos);
        DBUtils.checkInsert(contacts.size(), counts.length);

        int totalCount = 0;
        for (int i = 0; i < counts.length; i++) {
            int numInserted = counts[i];
            String email = contacts.get(i).getEmail();
            if (numInserted == 0) {
                log.warn(padding + "Mailing list: already contains entry with email '{}'", email);
            } else if (numInserted > 1) {
                log.error(padding + "Mailing list: expected to insert 1 row but did {} for email '{}'", numInserted, email);
            } else {
                totalCount++;
            }
        }

        log.info(padding + "Mailing list: finished loading {} entries", totalCount);
    }

    public void processParticipantFiles(Report report) {
        log.info("");

        Set<String> filenames = fileReader.listParticipantFiles();
        log.info("Found {} participant files", filenames.size());

        int count = 1;
        int total = filenames.size();
        for (var filename : filenames) {
            log.info("({}/{}) Working on participant file: {}", count, total, filename);
            var data = gson.fromJson(fileReader.readContent(filename), MemberFile.class);
            var row = report.newRow();

            try {
                TransactionWrapper.useTxn(handle -> processParticipant(handle, data, row));
            } catch (Exception e) {
                if (isProdRun) {
                    throw e;
                } else {
                    log.error("Error while processing participant file, continuing", e);
                }
            }

            if (!row.isExistingUser()) {
                try {
                    // Auth0 has rate limits, so add in some buffer.
                    long timeout = isProdRun ? 250L : 1000L;
                    TimeUnit.MILLISECONDS.sleep(timeout);
                } catch (InterruptedException e) {
                    throw new LoaderException(e);
                }
            }

            count++;
        }

        report.finish();
    }

    private void processParticipant(Handle handle, MemberFile data, Report.Row row) {
        String padding = "  ";

        var participant = data.getMemberWrapper();
        String email = userLoader.getOrGenerateDummyEmail(participant);
        String altPid = participant.getAltPid();
        String shortId = participant.getShortId();
        row.init(altPid, shortId, email);
        log.info(padding + "[user] altpid={}, shortid={}, surveys={}", altPid, shortId, data.getNumSurveys());

        User user = userLoader.findUserByAltPid(handle, altPid);
        if (user != null) {
            log.warn(padding + "- User has already been loaded, skipping: userGuid={}", user.getGuid());
            row.setUserGuid(user.getGuid());
            row.setUserHruid(user.getHruid());
            row.setExistingUser(true);
            row.setSkipped(true);
            return;
        }

        String auth0Connection = cfg.getString(LoaderConfigFile.AUTH0_CONNECTION);
        boolean createAccount = cfg.getBoolean(LoaderConfigFile.CREATE_AUTH0_ACCOUNTS);
        var auth0User = userLoader.findAuth0UserByEmail(auth0Connection, email);
        if (auth0User != null && auth0User.getId() != null && !auth0User.getId().isBlank()) {
            log.info(padding + "- Auth0 account exists: email={}, auth0UserId={}", email, auth0User.getId());
            row.setAuth0UserId(auth0User.getId());
            row.setExistInAuth0(true);
        } else if (createAccount) {
            log.info(padding + "- Auth0 account doesn't exist but creation flag is enabled");
            auth0User = userLoader.createAuth0Account(auth0Connection, email);
            log.info(padding + "- Created auth0 account: email={}, auth0UserId={}", email, auth0User.getId());
            row.setAuth0UserId(auth0User.getId());
            row.setExistInAuth0(false);
        } else {
            throw new LoaderException("Expected auth0 account to exist but could not be found for email: " + email);
        }

        String languageCode = mapping.getParticipant().getDefaultLanguage();
        boolean addedToMetadata = userLoader.updateAuth0UserMetadata(auth0User, languageCode);
        if (addedToMetadata) {
            log.info(padding + "- Updated auth0 user metadata with language: {}", languageCode);
        } else {
            log.info(padding + "- Auth0 user metadata already has language set");
        }

        user = userLoader.createLegacyUser(handle, participant, auth0User.getId(), languageCode);
        log.info(padding + "- Created migration user: id={}, guid={}, hruid={}",
                user.getId(), user.getGuid(), user.getHruid());
        row.setUserGuid(user.getGuid());
        row.setUserHruid(user.getHruid());

        userLoader.registerUserInStudy(handle, studyGuid, user.getGuid(), participant);
        log.info(padding + "- Registered user {} in study {}", user.getGuid(), studyGuid);

        String inactiveReason = participant.getInactiveReason();
        if (inactiveReason != null && inactiveReason.equals(WITHDREW_REASON_CODE)) {
            userLoader.withdrawUserFromStudy(handle, studyGuid, user.getGuid(), participant);
            log.info(padding + "- User had withdrew so marking as exited from study at {}", participant.getLastModified());
            row.setWithdrew(true);
        }

        for (var activityMapping : mapping.getActivities()) {
            processActivity(handle, user, activityMapping, data, row);
        }

        row.setSuccess(true);
    }

    private void processActivity(Handle handle, User user, MappingActivity activity, MemberFile data, Report.Row row) {
        String padding = "  ";

        String sourceSurveyName = activity.getSource();
        var survey = data.getSurveyWrapper(sourceSurveyName);
        if (survey == null) {
            log.info(padding + "Participant does not have survey {}, continuing", sourceSurveyName);
            return;
        }

        log.info(padding + "[{}] created={}, completed={}, updated={}", sourceSurveyName,
                survey.getCreated(), survey.getFirstCompleted(), survey.getLastUpdated());
        long activityId = getActivityId(handle, activity.getActivityCode());
        var latestVersionDto = getLatestVersion(handle, activityId);
        if (survey.getFirstCompleted() == null && survey.getCreated().toEpochMilli() < latestVersionDto.getRevStart()) {
            // They haven't submitted survey and they're on the old version.
            // Let's skip their data and create a blank instance of new version for them.
            var instanceDto = createBlankInstance(handle, user, activityId, survey);
            log.info(padding + "- Survey is not submitted and is an old version");
            log.info(padding + "- Created new blank instance: id={}, guid={}", instanceDto.getId(), instanceDto.getGuid());
            row.setBlankInstance(true);
            return;
        }

        var instanceDto = createMigratedInstance(handle, user, activity, survey, null);
        log.info(padding + "- Created activity instance: id={}, guid={}", instanceDto.getId(), instanceDto.getGuid());
        loadAnswers(handle, user, instanceDto, activity, survey);

        for (var nestedMapping : activity.getNestedActivities()) {
            switch (nestedMapping.getNestedType()) {
                case KEYS:
                    processNestedActivityKeys(handle, user, instanceDto, nestedMapping, survey);
                    break;
                case LIST:
                    processNestedActivityList(handle, user, instanceDto, nestedMapping, survey);
                    break;
                default:
                    throw new LoaderException("Unhandled nested activity type: " + nestedMapping.getNestedType());
            }
        }
    }

    private void processNestedActivityKeys(Handle handle, User user, ActivityInstanceDto parentInstanceDto,
                                           MappingActivity nestedActivity, SurveyWrapper parentSurvey) {
        String padding = "  ";

        // KEYS means data is in the parent survey itself, so we use the same object for data, including timestamps.
        log.info(padding + "[{}] parent={}", nestedActivity.getActivityCode(), parentInstanceDto.getActivityCode());

        var instanceDto = createMigratedInstance(handle, user, nestedActivity, parentSurvey, parentInstanceDto.getId());
        log.info(padding + "- Created nested activity instance with id={}, guid={}", instanceDto.getId(), instanceDto.getGuid());

        loadAnswers(handle, user, instanceDto, nestedActivity, parentSurvey);
    }

    private void processNestedActivityList(Handle handle, User user, ActivityInstanceDto parentInstanceDto,
                                           MappingActivity nestedActivity, SurveyWrapper parentSurvey) {
        String padding = "  ";

        List<ObjectWrapper> nestedList = parentSurvey.getObjectList(nestedActivity.getNestedList());
        if (nestedList == null || nestedList.isEmpty()) {
            // We should create an empty nested instance. Since it's empty, we should only have a created timestamp,
            // so we create a new survey wrapper here. For the actual timestamp, let's use the parent's one.
            var object = new JsonObject();
            object.add("ddp_created", new JsonPrimitive(parentSurvey.getCreated().toString()));
            var survey = new SurveyWrapper(object);
            var instanceDto = createMigratedInstance(handle, user, nestedActivity, survey, parentInstanceDto.getId());
            log.info(padding + "[{}] parent={}", nestedActivity.getActivityCode(), parentInstanceDto.getActivityCode());
            log.info(padding + "- Created empty nested activity instance with id={}, guid={}",
                    instanceDto.getId(), instanceDto.getGuid());
            return;
        }

        int number = 1;
        for (var nested : nestedList) {
            // Each nested object becomes the survey object to use for pulling out data. Use same timestamps as parent.
            log.info(padding + "[{}] parent={} ({})", nestedActivity.getActivityCode(), parentInstanceDto.getActivityCode(), number);
            // Nested instances are sorted by creation time, so add a small time shift so things are ordered properly.
            var instanceDto = createMigratedInstance(handle, user, nestedActivity, parentSurvey, parentInstanceDto.getId(), number);
            log.info(padding + "- Created nested activity instance with id={}, guid={}, created={}",
                    instanceDto.getId(), instanceDto.getGuid(), Instant.ofEpochMilli(instanceDto.getCreatedAtMillis()));
            loadAnswers(handle, user, instanceDto, nestedActivity, nested);
            number++;
        }
    }

    private ActivityInstanceDto createBlankInstance(Handle handle, User user, long activityId, SurveyWrapper survey) {
        // We use the higher-level DAO so we create a blank parent instance along with any nested activity instances
        // it needs. This way, when participant views the new instance, everything will be there.
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        return instanceDao.insertInstance(
                activityId, user, user,
                survey.getSubmissionId(),
                survey.getSessionId(),
                survey.getVersion());
    }

    private ActivityInstanceDto createMigratedInstance(Handle handle, User user, MappingActivity activity,
                                                       SurveyWrapper survey, Long parentInstanceId) {
        return createMigratedInstance(handle, user, activity, survey, parentInstanceId, 0);
    }

    private ActivityInstanceDto createMigratedInstance(Handle handle, User user, MappingActivity activity,
                                                       SurveyWrapper survey, Long parentInstanceId, long shiftMillis) {
        long activityId = getActivityId(handle, activity.getActivityCode());
        long createdAtMillis = survey.getCreated().toEpochMilli() + shiftMillis;
        Instant completedAt = survey.getFirstCompleted();
        Instant updatedAt = survey.getLastUpdated();

        // Use low-level interface so we don't trigger any side-effects like events or nested activities.
        var jdbiInstance = handle.attach(JdbiActivityInstance.class);
        var jdbiInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);

        long instanceId = jdbiInstance.insert(
                activityId, user.getId(), jdbiInstance.generateUniqueGuid(),
                null, createdAtMillis, null, parentInstanceId,
                survey.getSubmissionId(), survey.getSessionId(), survey.getVersion());
        long statusId = jdbiInstanceStatus.insert(instanceId, InstanceStatusType.CREATED, createdAtMillis, user.getId());

        if (completedAt != null) {
            boolean addedDelta = false;
            long completedAtMillis = completedAt.toEpochMilli() + shiftMillis;
            if (completedAtMillis < createdAtMillis) {
                throw new LoaderException("ddp_firstcompleted is less than ddp_created");
            } else if (completedAtMillis == createdAtMillis) {
                // Pepper doesn't allow statuses to have same timestamp, so we add a small delta here.
                completedAtMillis += 1;
                addedDelta = true;
            }
            statusId = jdbiInstanceStatus.insert(instanceId, InstanceStatusType.COMPLETE, completedAtMillis, user.getId());
            DBUtils.checkUpdate(1, jdbiInstance.updateFirstCompletedAtIfNotSet(instanceId, completedAtMillis));
            if (updatedAt != null) {
                // Shift this one too if we shifted the above one, so we don't get into situation where updated < completed.
                long updatedAtMillis = updatedAt.toEpochMilli() + (addedDelta ? 1 : 0) + shiftMillis;
                if (updatedAtMillis < completedAtMillis) {
                    throw new LoaderException("ddp_lastupdated is less than ddp_firstcompleted");
                } else if (updatedAtMillis > completedAtMillis) {
                    DBUtils.checkUpdate(1, jdbiInstanceStatus.updateTimestampByStatusId(statusId, completedAtMillis));
                }
            }
        } else if (updatedAt != null) {
            long updatedAtMillis = updatedAt.toEpochMilli() + shiftMillis;
            if (updatedAtMillis < createdAtMillis) {
                throw new LoaderException("ddp_lastupdated is less than ddp_created");
            } else if (updatedAtMillis == createdAtMillis) {
                updatedAtMillis += 1;
            }
            jdbiInstanceStatus.insert(instanceId, InstanceStatusType.IN_PROGRESS, updatedAtMillis, user.getId());
        }

        return jdbiInstance.getByActivityInstanceId(instanceId).orElseThrow(() ->
                new LoaderException("Could not find newly created activity instance with id " + instanceId));
    }

    private void loadAnswers(Handle handle, User user, ActivityInstanceDto instanceDto,
                             MappingActivity activity, ObjectWrapper survey) {
        String padding = "  - ";
        var answerDao = handle.attach(AnswerDao.class);
        for (var question : activity.getQuestions()) {
            Answer answer = question.extractAnswer(survey);
            if (answer != null) {
                answerDao.createAnswer(user.getId(), instanceDto.getId(), answer);
                // log.info(padding + "[{}] type={}, source={}, answerId={}, answerGuid={}",
                //         question.getTarget(), question.getType(), question.getSource(),
                //         answer.getAnswerId(), answer.getAnswerGuid());
            }
        }
    }

    private long getActivityId(Handle handle, String activityCode) {
        if (!activityCodeToId.containsKey(activityCode)) {
            ActivityDto activityDto = handle.attach(JdbiActivity.class)
                    .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                    .orElseThrow(() -> new LoaderException("Could not find activity " + activityCode));
            activityCodeToId.put(activityCode, activityDto.getActivityId());
        }
        return activityCodeToId.get(activityCode);
    }

    private ActivityVersionDto getLatestVersion(Handle handle, long activityId) {
        if (!activityIdToLatestVersion.containsKey(activityId)) {
            ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                    .getActiveVersion(activityId)
                    .orElseThrow(() -> new LoaderException("Could not find latest version for activity " + activityId));
            activityIdToLatestVersion.put(activityId, versionDto);
        }
        return activityIdToLatestVersion.get(activityId);
    }

    public void processDsmFiles() {
        log.info("");
        Set<String> files = fileReader.listParticipantFiles();
        log.info("Found {} participant files for dsm data", files.size());
        int count = 1;
        int total = files.size();
        for (var filename : files) {
            log.info("({}/{}) Working on participant file for dsm data: {}", count, total, filename);
            var data = gson.fromJson(fileReader.readContent(filename), MemberFile.class);
            try {
                processParticipantDsmData(data.getMemberWrapper());
            } catch (Exception e) {
                if (isProdRun) {
                    throw e;
                } else {
                    log.error("Error while processing participant file for dsm data, continuing", e);
                }
            }
            count++;
        }

        log.info("");
        files = fileReader.listFamilyMemberFiles();
        log.info("Found {} family member files for dsm data", files.size());
        count = 1;
        total = files.size();
        for (var filename : files) {
            log.info("({}/{}) Working on family member file for dsm data: {}", count, total, filename);
            var data = gson.fromJson(fileReader.readContent(filename), MemberFile.class);
            try {
                DsmDataLoader.useTxn(dsmHandle -> processFamilyMemberDsmData(dsmHandle, data.getMemberWrapper()));
            } catch (Exception e) {
                if (isProdRun) {
                    throw e;
                } else {
                    log.error("Error while processing family member file for dsm data, continuing", e);
                }
            }
            count++;
        }
    }

    private void processParticipantDsmData(MemberWrapper participant) {
        String altPid = participant.getAltPid();
        if (StringUtils.isBlank(altPid)) {
            if (isProdRun) {
                throw new LoaderException("Participant is missing altpid");
            } else {
                log.error("  Participant is missing altpid, skipping");
                return;
            }
        }

        String familyId = participant.getFamilyId();
        if (StringUtils.isBlank(familyId)) {
            log.error("  Participant is missing family_id, skipping");
            return;
        }

        DsmDataLoader.useTxn(dsmHandle -> {
            loadDsmParticipantData(dsmHandle, altPid, mapping, participant);
            loadDsmFormData(dsmHandle, altPid, mapping, participant);
        });
        familyIdToParticipantAltPid.put(familyId, altPid);
        log.info("  - Assigned family_id={} to participant altpid={}", familyId, altPid);
    }

    private void processFamilyMemberDsmData(Handle dsmHandle, MemberWrapper member) {
        String familyId = member.getFamilyId();
        if (StringUtils.isBlank(familyId)) {
            log.error("  Family member is missing family_id, skipping");
            return;
        }

        String altPid = familyIdToParticipantAltPid.get(familyId);
        if (StringUtils.isBlank(altPid)) {
            if (isProdRun) {
                throw new LoaderException("Could not find participant altpid for family_id: " + familyId);
            } else {
                log.error("  Could not find participant altpid for family_id={}, skipping", familyId);
                return;
            }
        }

        log.info("  - Using participant altpid={}", altPid);
        loadDsmFormData(dsmHandle, altPid, mapping, member);
    }

    private void loadDsmParticipantData(Handle dsmHandle, String participantAltPid, Mapping mapping, MemberWrapper participant) {
        Map<String, String> data = new HashMap<>();
        for (var field : mapping.getDsmParticipantFields()) {
            String value = participant.getString(field.getSource());
            if (value != null) {
                data.put(field.getTarget(), value);
            }
        }

        log.info("  - Participant has additional record data with {} field values", data.size());
        String jsonData = !data.isEmpty() ? gson.toJson(data) : null;

        long dsmParticipantId = dsmLoader.createDsmParticipant(dsmHandle, studyGuid, participantAltPid);
        log.info("  - Created dsm participant with id={}", dsmParticipantId);

        long recordId = dsmLoader.createParticipantRecord(dsmHandle, studyGuid, dsmParticipantId, jsonData);
        log.info("  - Created participant record with id={}", recordId);
    }

    private void loadDsmFormData(Handle dsmHandle, String participantAltPid, Mapping mapping, MemberWrapper member) {
        Map<String, String> data = new HashMap<>();
        for (var field : mapping.getDsmFormFields()) {
            if ("datstat_masterpid".equalsIgnoreCase(field.getSource())) {
                var obj = member.getObject(field.getSource());
                if (obj != null) {
                    String uri = obj.getString("Uri");
                    String pid = Path.of(uri).getFileName().toString();
                    data.put(field.getTarget(), pid);
                }
            } else if ("datstat_dateofbirth".equalsIgnoreCase(field.getSource())) {
                String value = member.getString(field.getSource());
                if (value != null) {
                    // Truncate the time portion since DOB is just the date.
                    value = value.contains("T") ? value.split("T")[0] : value;
                    data.put(field.getTarget(), value);
                }
            } else if ("medical_records_received".equalsIgnoreCase(field.getSource())) {
                String value = member.getString(field.getSource());
                if (value != null) {
                    value = value.equalsIgnoreCase("N/A") ? "NA" : value;
                    data.put(field.getTarget(), value);
                }
            } else {
                String value = member.getString(field.getSource());
                if (value != null) {
                    data.put(field.getTarget(), value);
                }
            }
        }

        log.info("  - Member has dsm form data with {} field values", data.size());
        if (!data.isEmpty()) {
            String json = gson.toJson(data);
            long id = dsmLoader.loadFormData(dsmHandle, studyGuid, participantAltPid, json);
            log.info("  - Inserted member dsm data with id={}", id);
        }
    }

    public void fixFamilyNotes() {
        log.info("");
        familyIdToParticipantAltPid.clear();

        Set<String> files = fileReader.listParticipantFiles();
        log.info("Found {} participant files", files.size());
        int count = 0;
        int total = files.size();
        for (var filename : files) {
            count++;
            log.info("({}/{}) Looking at participant file: {}", count, total, filename);
            var data = gson.fromJson(fileReader.readContent(filename), MemberFile.class);
            try {
                var participant = data.getMemberWrapper();
                String altPid = participant.getAltPid();
                if (StringUtils.isBlank(altPid)) {
                    if (isProdRun) {
                        throw new LoaderException("Participant is missing altpid");
                    } else {
                        log.error("  Participant is missing altpid, skipping");
                        continue;
                    }
                }
                String familyId = participant.getFamilyId();
                if (StringUtils.isBlank(familyId)) {
                    log.error("  Participant is missing family_id, skipping");
                    continue;
                }
                familyIdToParticipantAltPid.put(familyId, altPid);
                log.info("  - Assigned family_id={} to participant altpid={}", familyId, altPid);
            } catch (Exception e) {
                if (isProdRun) {
                    throw e;
                } else {
                    log.error("Error while processing participant file, continuing", e);
                }
            }
        }

        log.info("");
        files = fileReader.listFamilyMemberFiles();
        log.info("Found {} family member files", files.size());
        count = 0;
        total = files.size();
        int numUpdated = 0;
        for (var filename : files) {
            count++;
            log.info("({}/{}) Working on family member file for family notes fix: {}", count, total, filename);
            var data = gson.fromJson(fileReader.readContent(filename), MemberFile.class);
            try {
                var member = data.getMemberWrapper();
                String familyId = member.getFamilyId();
                if (StringUtils.isBlank(familyId)) {
                    log.error("  Family member is missing family_id, skipping");
                    continue;
                }
                String altPid = familyIdToParticipantAltPid.get(familyId);
                if (StringUtils.isBlank(altPid)) {
                    if (isProdRun) {
                        throw new LoaderException("Could not find participant altpid for family_id: " + familyId);
                    } else {
                        log.error("  Could not find participant altpid for family_id={}, skipping", familyId);
                        continue;
                    }
                }
                log.info("  - Using participant altpid={}", altPid);

                if (!"SELF".equalsIgnoreCase(member.getMemberType())) {
                    log.info("  - Family member is not self/proband, skipping family notes fix");
                    continue;
                }

                Map<String, String> values = new HashMap<>();
                for (var field : mapping.getDsmParticipantFields()) {
                    String value = member.getString(field.getSource());
                    if (value != null) {
                        values.put(field.getTarget(), value);
                    }
                }
                log.info("  - Family member has {} field values", values.size());
                String jsonData = !values.isEmpty() ? gson.toJson(values) : null;

                DsmDataLoader.useTxn(dsmHandle -> {
                    Long dsmParticipantId = dsmLoader.findDsmParticipantId(dsmHandle, studyGuid, altPid);
                    if (dsmParticipantId == null) {
                        log.error("  Could not find participant id for altpid {}, skipping", altPid);
                        return;
                    } else {
                        log.info("  - Found dsm participant with id={}", dsmParticipantId);
                    }
                    dsmLoader.updateParticipantRecord(dsmHandle, dsmParticipantId, jsonData);
                    log.info("  - Updated participant record with new family notes json");
                });
                numUpdated++;
            } catch (Exception e) {
                if (isProdRun) {
                    throw e;
                } else {
                    log.error("Error while processing family member file for family notes fix, continuing", e);
                }
            }
        }

        log.info("Updated {} participant records for family notes fix", numUpdated);
    }
}
