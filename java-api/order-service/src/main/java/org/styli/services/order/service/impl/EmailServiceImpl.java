package org.styli.services.order.service.impl;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.styli.services.order.pojo.email.AttachmentDTO;
import org.styli.services.order.service.EmailService;
import org.styli.services.order.utility.Constants;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Log LOGGER = LogFactory.getLog(EmailServiceImpl.class);

    private static final String MIME_TEXT_PLAIN = "text/plain";
    private static final String MIME_TEXT_HTML = "text/html";

    @Autowired
    private SendGrid sendGridClient;

    @Value("${order.service.sendgrid.from_email}")
    private String fromEmail;

    /**
     * SendGrid trigger TEXT email with attachement
     * @return
     */
    @Override
    public Response sendTextWithAttachment(String to, String subject, String body, String fileName) {

        AttachmentDTO attachment = null;
        try {
            attachment = new AttachmentDTO();
            attachment.setAttachmentStream(new FileInputStream(fileName));
            attachment.setMimeType("application/csv");
            attachment.setFilename(fileName);
        } catch (FileNotFoundException e) {
        	LOGGER.error("Error while sending email with attachment",e.getCause());
        }

        return sendEmail(to, subject, new Content(MIME_TEXT_PLAIN, body), attachment);
    }

    /**
     * SendGrid trigger TEXT email
     * @return
     */
    @Override
    public Response sendText(String to, String subject, String body) {
        return sendEmail(to, subject, new Content(MIME_TEXT_PLAIN, body), null);
    }

    /**
     * SendGrid trigger HTML email
     * @return
     */
    @Override
    public Response sendHTML(String to, String subject, String body) {
        return sendEmail(to, subject, new Content(MIME_TEXT_HTML, body), null);
    }

    /**
     * SendGrid trigger email
     *
     * @param to String
     * @param subject String
     * @param content Content
     * @param attachment AttachmentDTO
     * @return Response
     */
    private Response sendEmail(String to, String subject, Content content, AttachmentDTO attachment) {
        String ccEmails = Constants.orderCredentials.getWalletUpdateCcEmail() !=  null
                ? Constants.orderCredentials.getWalletUpdateCcEmail()
                : "yasir.kotwal@landmarkgroup.com";
        Mail mail = new Mail(new Email(fromEmail), subject, new Email(to), content);
        if (!ccEmails.isEmpty()) {
            String[] emails = ccEmails.split(",");
            for (String email : emails) {
                mail.getPersonalization().get(0).addCc(new Email(email));
            }
        }
        Request request = new Request();
        Response response = null;
        if (ObjectUtils.isNotEmpty(attachment)) {
            addAttachment(mail, attachment);
        }
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            response = sendGridClient.api(request);
        } catch (IOException e) {
            LOGGER.error("Error in sending email: ", e);
        }
        if (Objects.nonNull(response) && response.getStatusCode() == 202) {
            LOGGER.info("Email (HTML) sent to "
                    + to + ", with subject : "
                    + subject + ". Status code : "
                    + response.getStatusCode() + ". Body : "
                    + response.getBody());
        } else {
            LOGGER.info("Email (HTML) not sent to "
                    + to + ", with subject : "
                    + subject + ". Please find above \"Error in sending email\" content to get error details.");
        }
        return response;
    }

    private void addAttachment(Mail mail, AttachmentDTO attachment) {
        final Attachments attachments = new Attachments.Builder(
                attachment.getFilename(),
                attachment.getAttachmentStream()).withType(attachment.getMimeType()) // "application/pdf"
                .build();
        mail.addAttachments(attachments);
    }

}
