package dab.utilities;


import android.graphics.Bitmap;

import androidx.annotation.NonNull;

public class Email {
    private String emailTo;
    private String emailFrom;
    private String emailCC;
    private String emailSubject;
    private String emailBody;
    private String imageAttachment;

    public Email(String to, String from, String cc, String subject, String body, String imageAttachment) {
        this.emailTo = to;
        this.emailFrom = from;
        this.emailCC = cc;
        this.emailSubject = subject;
        this.emailBody = body;
        this.imageAttachment = imageAttachment;
    }

    // Setters
    public void setEmailTo(String to) { this.emailTo = to; }
    public void setEmailFrom(String from) { this.emailFrom = from; }
    public void setEmailCC(String cc) { this.emailCC = cc; }
    public void setEmailBody(String body) { this.emailBody = body; }
    public void setEmailSubject(String subject) { this.emailSubject = subject; }
    public void setEmailAttachment(String imageAttachment) { this.imageAttachment = imageAttachment; }

    // Getters
    public String getEmailTo() { return this.emailTo; }
    public String getEmailFrom() { return this.emailFrom; }
    public String getEmailCC() { return this.emailCC; }
    public String getEmailBody() { return this.emailBody; }
    public String getEmailSubject() { return this.emailSubject; }
    public String getImageAttachment() { return this.imageAttachment; }

    @NonNull
    @Override
    public String toString() {
        String result = "EMAIL DETAILS\n";
        result += "Email To: " + this.emailTo + "\n";
        result += "Email From: " + this.emailFrom + "\n";
        result += "Email Body: " + this.emailBody + "\n";
        result += "Email Subject: " + this.emailSubject + "\n";
        return result;
    }
}