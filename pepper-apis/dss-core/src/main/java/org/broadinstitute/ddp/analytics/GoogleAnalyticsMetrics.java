package org.broadinstitute.ddp.analytics;

public class GoogleAnalyticsMetrics {

    public static final String EVENT_CATEGORY_USER_REGISTRATION = "user-registration";
    public static final String EVENT_ACTION_USER_REGISTRATION = "register-user";
    public static final String EVENT_LABEL_USER_REGISTRATION = "registration"; //studyGuid appended

    public static final String EVENT_CATEGORY_USER_LOGIN = "user-login";
    public static final String EVENT_ACTION_USER_LOGIN = "user-logged-in";
    public static final String EVENT_LABEL_USER_LOGIN = "login"; //studyGuid appended

    public static final String EVENT_CATEGORY_ACTIVITY_INSTANCE = "activity";
    public static final String EVENT_ACTION_ACTIVITY_INSTANCE = "activity-instance";
    public static final String EVENT_LABEL_ACTIVITY_INSTANCE = "get-activity-instance"; //studyGuid & activityCode appended

    public static final String EVENT_CATEGORY_PATCH_ANSWERS = "form-answers";
    public static final String EVENT_ACTION_PATCH_ANSWERS = "patch-form-answers";
    public static final String EVENT_LABEL_PATCH_ANSWERS = "patch-answers"; //studyGuid & activityCode appended

    public static final String EVENT_CATEGORY_PUT_ANSWERS = "form-answers";
    public static final String EVENT_ACTION_PUT_ANSWERS = "submit-form-answers";
    public static final String EVENT_LABEL_PUT_ANSWERS = "submit-form"; //studyGuid & activityCode appended

}
