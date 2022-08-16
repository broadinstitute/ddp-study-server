package org.broadinstitute.ddp.service;

import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.files.FileUpload;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileUploadNotificationEmailFactory {
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd.MM.yyyy");
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");

    public static Mail create(final StudyDto study, final String userGuid, final List<FileUpload> fileUploads) {
        return new Mail(
                new Email(study.getStudyEmail()),
                "Project Singular: New Participant File(s) Uploaded",
                new Email(study.getNotificationEmail()),
                new Content(
                        "text/html",
                        getBody(userGuid, fileUploads)));
    }

    private static String getBody(final String userGuid, final List<FileUpload> fileUploads) {
        return "Dear Project Singular Study Staff,<br /><br /"
                + "A Project Singular study participant recently uploaded a file or files. Please log in to DSM and access "
                + "the participant's profile page to review and download the file(s) indicated below.<br />"
                + getUploadsTable(userGuid, fileUploads);
    }

    private static String getUploadsTable(final String userGuid, final List<FileUpload> fileUploads) {
        final StringBuilder table = new StringBuilder(getHead());

        table.append("<table>");
        table.append(getTableHeader());

        StreamEx.of(fileUploads)
                .forEach(fileUpload -> table.append(getTableRow(userGuid, fileUpload)));

        return table.append("</table>").toString();
    }

    private static String getHead() {
        return "<head>"
                + "  <style>"
                + "    table {"
                + "      border-collapse: collapse;"
                + "      max-width: 900px;"
                + "      border: 1px solid #c3c3c3;"
                + "      font-size: 17px;"
                + "      margin: 0 auto;"
                + "    }"
                + "    td, th {"
                + "      border: 1px solid #c3c3c3;"
                + "      padding: 10px;"
                + "    }"
                + "    th {"
                + "      background-color: #f7f7f7;"
                + "    }"
                + "  </style>"
                + "</head>";
    }

    private static String getTableHeader() {
        return "<tr style=\"font-weight: 600; background-color: #f7f7f7;\">"
                + "<th>Participant ID</th>"
                + "<th>File Name</th>"
                + "<th>File Size</th>"
                + "<th>Date Uploaded</th>"
                + "<th>Time Uploaded</th>"
                + "</tr>";
    }

    private static String getTableRow(final String userGuid, final FileUpload fileUpload) {
        return "<tr>"
                + "<td>" + userGuid + "</td>"
                + "<td>" + fileUpload.getFileName() + "</td>"
                + "<td>" + fileUpload.getFileSize() + "</td>"
                + "<td>" + dateFormatter.format(Date.from(fileUpload.getCreatedAt())) + "</td>"
                + "<td>" + timeFormatter.format(Date.from(fileUpload.getCreatedAt())) + "</td>"
                + "</tr>";
    }
}
