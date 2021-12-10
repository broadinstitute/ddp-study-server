package org.broadinstitute.lddp.email;

public class SendGridEventData {

    private String ip;
    private String url;
    private String email;
    private String event;
    private long timestamp;
    private String useragent;
    private String ddp_log_ver;
    private String sg_event_id;
    private String ddp_env_type;
    private String sg_message_id;
    private String ddp_email_template;
    private String smtp_id;

    public SendGridEventData(String ip, String url, String email, String event, long timestamp, String useragent, String ddp_log_ver,
                         String sg_event_id, String ddp_env_type, String sg_message_id, String ddp_email_template, String smtp_id) {
        this.ip = ip;
        this.url = url;
        this.email = email;
        this.event = event;
        this.timestamp = timestamp;
        this.useragent = useragent;
        this.ddp_log_ver = ddp_log_ver;
        this.sg_event_id = sg_event_id;
        this.ddp_env_type = ddp_env_type;
        this.sg_message_id = sg_message_id;
        this.ddp_email_template = ddp_email_template;
        this.smtp_id = smtp_id;
    }

    public String getIp() {
        return ip;
    }

    public String getUrl() {
        return url;
    }

    public String getEmail() {
        return email;
    }

    public String getEvent() {
        return event;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUseragent() {
        return useragent;
    }

    public String getDdp_log_ver() {
        return ddp_log_ver;
    }

    public String getSg_event_id() {
        return sg_event_id;
    }

    public String getDdp_env_type() {
        return ddp_env_type;
    }

    public String getSg_message_id() {
        return sg_message_id;
    }

    public String getDdp_email_template() {
        return ddp_email_template;
    }

    public String getSmtp_id() {
        return smtp_id;
    }
}
