package org.styli.services.customer.service.impl;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.styli.services.customer.service.EmailService;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import java.io.IOException;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Log LOGGER = LogFactory.getLog(EmailServiceImpl.class);
    private static final String MIME_TEXT_PLAIN = "text/plain";
    

    @Autowired
    private SendGrid sendGridClient;
    
    @Value("${customer.service.sendgrid.from_email}")
    private String fromEmail;

    @Override
    public Response sendText(String to, String subject, String body) {
        return sendEmail(to, subject, new Content(MIME_TEXT_PLAIN, body));
    }

    private Response sendEmail(String to, String subject, Content content) {
        if (fromEmail == null || fromEmail.isEmpty()) {
            LOGGER.info("Registration OTP : From email address is not configured.");
            return null; 
        }

        Mail mail = new Mail(new Email(fromEmail), subject, new Email(to), content);

        Request request = new Request();
        Response response = null;
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            response = sendGridClient.api(request);
        } catch (IOException e) {
            LOGGER.info("Registration OTP : Error in sending email to " + to + " with subject " + subject, e);
        }

        if (response != null && response.getStatusCode() == 202) {
            LOGGER.info("Registration OTP : Email sent to " + to + " with subject: " + subject + ". Status code: "
                    + response.getStatusCode() + ". Body: " + response.getBody());
        } else {
            LOGGER.warn("Registration OTP : Email not sent to " + to + " with subject: " + subject + ". Response: "
                    + (response != null ? response.getBody() : "No response received."));
        }
        return response;
    }

}
