package dab.utilities;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class EmailManager extends AsyncTask<Void,Void,Void> {
    private String smtpEmail;
    private String smtpPassword;

    private Properties props;
    private Email email;

    public EmailManager(String smtpEmail, String smtpPassword) {
        this.smtpEmail = smtpEmail;
        this.smtpPassword = smtpPassword;
    }

    public void configureSMTP(boolean authRequired, boolean enableTLS, String smtpServer, int smtpPort) {
        props = new Properties();

        props.put("mail.smtp.auth", authRequired);
        props.put("mail.smtp.starttls.enable", enableTLS);
        props.put("mail.smtp.host", smtpServer);
        props.put("mail.smtp.port", smtpPort);
    }

    public void setEmailContents(String emailTo, String emailFrom, String emailCC, String subject, String body, String image) {
        this.email = new Email(emailTo, emailFrom, emailCC, subject, body, image);
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Session session = Session.getDefaultInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {

                        return new PasswordAuthentication(smtpEmail, smtpPassword);
                    }
                });

        try {
            MimeMessage mm = new MimeMessage(session);

            String embeddedBody = "";
            if (email.getEmailBody() != null && email.getEmailBody() != "") {
                embeddedBody = embeddedBody + email.getEmailBody() + "\n";
                // TODO: Embed image into body
            }

            mm.setFrom(new InternetAddress(smtpEmail));
            mm.addRecipient(Message.RecipientType.TO, new InternetAddress(email.getEmailTo()));
            mm.addRecipient(Message.RecipientType.CC, new InternetAddress(email.getEmailCC()));
            mm.setSubject(email.getEmailSubject());

            // Create the message part
            MimeBodyPart messageBodyPart = new MimeBodyPart();

            // Now set the actual message
            messageBodyPart.setText(email.getEmailBody());

            // Create a multipar message
            Multipart multipart = new MimeMultipart();

            // Set text message part
            multipart.addBodyPart(messageBodyPart);

            // Part two is attachment
            messageBodyPart = new MimeBodyPart();
            String filename = email.getImageAttachment();
            DataSource source = new FileDataSource(filename);
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName(filename);
            multipart.addBodyPart(messageBodyPart);

            // Send the complete message parts
            mm.setContent(multipart);

            Transport.send(mm);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
