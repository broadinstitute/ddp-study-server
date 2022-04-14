package org.broadinstitute.ddp.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

class Report {

    private List<Row> rows = new ArrayList<>();
    private boolean isFinished = false;

    public static String defaultFilename(String studyGuid) {
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").withZone(ZoneOffset.UTC);
        return String.format("%s_migration_%s.csv", studyGuid.toLowerCase(), fmt.format(now));
    }

    public Row newRow() {
        var row = new Row();
        rows.add(row);
        return row;
    }

    public void finish() {
        isFinished = true;
    }

    public boolean isPartial() {
        return !isFinished;
    }

    public void write(String filename) {
        try {
            var writer = Files.newBufferedWriter(Path.of(filename));
            var printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                    .withNullString("")
                    .withRecordSeparator("\n")
                    .withFirstRecordAsHeader());

            printer.printRecord(
                    "altpid", "shortid", "email",
                    "auth0_user_id", "user_guid", "user_hruid",
                    "is_existing_user", "is_exist_in_auth0",
                    "is_blank_instance", "is_withdrew",
                    "is_skipped", "is_success");

            for (var row : rows) {
                printer.printRecord(
                        row.altpid,
                        row.shortid,
                        row.email,
                        row.auth0UserId,
                        row.userGuid,
                        row.userHruid,
                        Boolean.toString(row.isExistingUser),
                        Boolean.toString(row.isExistInAuth0),
                        Boolean.toString(row.isBlankInstance),
                        Boolean.toString(row.isWithdrew),
                        Boolean.toString(row.isSkipped),
                        Boolean.toString(row.isSuccess));
            }

            printer.flush();
            printer.close();
        } catch (IOException e) {
            throw new LoaderException(e);
        }
    }

    public static class Row {
        private String altpid;
        private String shortid;
        private String email;
        private String auth0UserId;
        private String userGuid;
        private String userHruid;
        private boolean isExistingUser;
        private boolean isExistInAuth0;
        private boolean isBlankInstance;
        private boolean isWithdrew;
        private boolean isSkipped;
        private boolean isSuccess;

        public void init(String altpid, String shortid, String email) {
            this.altpid = altpid;
            this.shortid = shortid;
            this.email = email;
        }

        public void setAuth0UserId(String auth0UserId) {
            this.auth0UserId = auth0UserId;
        }

        public void setUserGuid(String userGuid) {
            this.userGuid = userGuid;
        }

        public void setUserHruid(String userHruid) {
            this.userHruid = userHruid;
        }

        public boolean isExistingUser() {
            return isExistingUser;
        }

        public void setExistingUser(boolean existingUser) {
            isExistingUser = existingUser;
        }

        public void setExistInAuth0(boolean existInAuth0) {
            isExistInAuth0 = existInAuth0;
        }

        public void setBlankInstance(boolean blankInstance) {
            isBlankInstance = blankInstance;
        }

        public void setWithdrew(boolean withdrew) {
            isWithdrew = withdrew;
        }

        public void setSkipped(boolean skipped) {
            isSkipped = skipped;
        }

        public void setSuccess(boolean success) {
            isSuccess = success;
        }
    }
}
