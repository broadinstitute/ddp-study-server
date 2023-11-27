package org.broadinstitute.ddp.customexport;

/*
 * This package contains code necessary to run a customized export of data for a particular study.
 * With this feature, it is possible to specify a subset of participant fields to exclude, an activity to include, a subset of
 * activity metadata fields to exclude, a subset of activity fields to exclude, and a subset of activity fields to place before all other
 * activity fields.  This customized export will run on a schedule and export a CSV file to the desired location for a bucket.  When the
 * export is complete, an email notification will be sent to the interested party to let them know the export is ready.
 *
 * While this customized export code could potentially be used for any study, this is intended to preserve some legacy behavior for an
 * existing study.  This is an exception to the general rule of making pepper-apis contain only platform-level code necessitated by the
 * details of this particular study.  It would be possible to add the ability to configure this to study builder in order to allow other
 * studies to use this functionality, but for now we have chosen not to implement that particular functionality.  It should be relatively
 * straightforward to adapt this code to include additional flexibility despite the study-specific purpose of this code.
 *
 * This code is designed to take in parameters related to the export from a file in Secret Manager.  This file should be named
 * custom-export and should be associated with the Google project for the environment.  Anyone who wishes to configure this
 * for their own environment should include the following fields:
 * - bucketName: Name of the bucket in which to place the export file
 * - filePath: Optional.  Path to location in which file will be placed
 * - baseFileName: The file name will be [time stamp][baseFileName].csv
 * - guid: The guid of the study to export
 * - activity: the code of the activity to export
 * - status: The export will include only participants whose status for the specified activity matches this value
 * - excludedActivityFields: List of activity fields to leave out of the export
 * - excludedParticipantFields: List of participant fields to leave out of the export
 * - firstFields: Activity fields to include at the beginning of the export
 * - email:
 *      - fromName: Name of the sender
 *      - fromEmail: Email address of the sender
 *      - toName: Name of the recipient
 *      - toEmail: Email address of the recipient
 *      - successSubject: Subject line for notification of successful export
 *      - successContent: Content of message for notification of successful export
 *      - skipSubject: Subject line for notification of export skipped due to lack of new data
 *      - skipContent: Content of message for notification of skipped export
 *      - errorSubject: Subject line for notification of failed export
 *      - errorContent: Content of message for notification of failed export
 *      - sendGridToken: Token of SendGrid account to use to send the notification message
 */
