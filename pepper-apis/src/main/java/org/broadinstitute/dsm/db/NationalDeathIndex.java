package org.broadinstitute.dsm.db;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.model.NDIUploadObject;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class NationalDeathIndex {

    private static final Logger logger = LoggerFactory.getLogger(NationalDeathIndex.class);

    private static final String SQL_SELECT_LAST_CONTROL_NUMBER = "SELECT ndi_control_number FROM ddp_ndi  order by ndi_id desc limit 1";
    private static final String SQL_INSERT_CONTROL_NUMBER = "INSERT INTO ddp_ndi (ddp_participant_id, ndi_control_number, last_changed, changed_by) VALUES ";
    private static final String FIRST_NDI = "0000000000";
    private static final String LAST_NDI = "zzzzzzzzzz";
    private static final int LAST_NAME_MAX_LENGHT = 20;
    private static final int FIRST_NAME_MAX_LENGHT = 15;

    public static String generateNextControlNumber(String previous) {
        if (previous == null) {
            return FIRST_NDI;
        }
        if (previous.equals(LAST_NDI)) {
            throw new RuntimeException("Wow! You have run out of ndi control numbers for length 10!");
        }
        char[] current = previous.toCharArray();
        int i = 9;
        while (current[i] == 'z') {
            current[i] = '0';
            i--;
        }
        char c = getNextChar(current[i]);
        current[i] = c;
        return String.valueOf(current);
    }

    private static char getNextChar(char c) {
        if (c == '9') {
            return 'A';
        }
        if (c == 'Z') {
            return 'a';
        }
        return ++c;
    }

    public static List<String> getAllNdiControlStrings(int linesInRequestFile) {
        List<String> controlNumbers = new ArrayList<>(linesInRequestFile);
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String lastControlNumber = null;
            try {
                PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_LAST_CONTROL_NUMBER);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbVals.resultValue = rs.getString(DBConstants.NDI_CONTROL_NUMBER);
                }
                else {
                    dbVals.resultValue = null;
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }

            return dbVals;
        });
        if (result.resultException == null) {
            String lastControlNumber = result.resultValue == null ? null : String.valueOf(result.resultValue);
            for (int i = 0; i < linesInRequestFile; i++) {
                String s = generateNextControlNumber(lastControlNumber);
                controlNumbers.add(s);
                lastControlNumber = s;
            }
            return controlNumbers;
        }
        else {
            throw new RuntimeException("Error getting the last inserted ndi control number. ", result.resultException);
        }
    }

    public static String createOutputTxtFile(List<NDIUploadObject> requests, String userId) throws IOException {
        int size = requests.size();
        List<String> controlNumbers = getAllNdiControlStrings(size);
        StringBuilder textToPutInTextFile = new StringBuilder();
        String sqlValues = "";
        for (int i = 0; i < size; i++) {
            StringBuilder line = new StringBuilder(100);
            NDIUploadObject participant = requests.get(i);
            String controlNumber = controlNumbers.get(i);
            String lastName = participant.getLastName();
            String paddedLastName = lastName;
            if (lastName.length() > LAST_NAME_MAX_LENGHT) {
                paddedLastName = lastName.substring(0, LAST_NAME_MAX_LENGHT);
            }
            else {
                paddedLastName = String.format("%-20s", lastName);
            }
            line.append(paddedLastName);
            String firstName = participant.getFirstName();
            String paddedFirstName = firstName;
            if (firstName.length() > FIRST_NAME_MAX_LENGHT) {
                paddedFirstName = firstName.substring(0, FIRST_NAME_MAX_LENGHT);
            }
            else {
                paddedFirstName = String.format("%-15s", firstName);
            }
            line.append(paddedFirstName);
            String middle = participant.getMiddle().length() == 1 ? participant.getMiddle() : " ";
            line.append(middle);
            String space = "";
            space = String.format("%-9s", space);
            line.append(space);//ssn
            space = "";
            line.append(participant.getMonth());
            line.append(participant.getDay());
            line.append(participant.getYear());
            space = String.format("%-28s", space);
            line.append(space);
            space = "";
            line.append(controlNumber);
            space = String.format("%-9s", space);
            line.append(space);//optional stuff and 2 blanks
            space = "";
            line.append("\n");
            textToPutInTextFile.append(line.toString());
            sqlValues += "( '" + participant.getDdpParticipantId() + "', '" + controlNumber + "', " + System.currentTimeMillis() + ", '" + userId + "' ), ";
        }
        String finalStatement = SQL_INSERT_CONTROL_NUMBER + sqlValues;
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                String stmtString = finalStatement.substring(0, finalStatement.length() - 2);
                PreparedStatement stmt = conn.prepareStatement(stmtString);
                int i = stmt.executeUpdate();
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (result.resultException != null) {

            throw new RuntimeException("Error inserting control numbers into DB ", result.resultException);
        }
        else {
            logger.info("Created NDI file");
            return textToPutInTextFile.toString();
        }
    }
}
