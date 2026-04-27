package org.styli.services.order.helper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.order.utility.consulValues.FromEmail;
import org.styli.services.order.utility.consulValues.ServiceConfigs;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class EmailHelper {

  public static final String CONTENT_TYPE_PLAIN = "text/plain";
  public static final String CONTENT_TYPE_HTML = "text/html";
  public static final String CONTENT_TYPE_PDF = "application/pdf";
  public static final String CONTENT_TYPE_PNG = "image/png";

  public static final String VALID_EMAIL_ADDRESS_REGEX =
      "[a-zA-Z0-9._-][a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}";

  private static final Log LOGGER = LogFactory.getLog(EmailHelper.class);
  private static final List<Integer> SUCCESS_CODES = Arrays.asList(200, 202);

  private final ObjectMapper mapper = new ObjectMapper();

  @Value("${sendgrid.key}")
  private String sendgridKey;

  SendGrid sendGrid;

  @PostConstruct
  public void init() {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    sendGrid = new SendGrid(sendgridKey);
  }

  public boolean sendEmail(
      String to,
      String toName,
      String content,
      String contentType,
      String subject,
      String langCode) {
    boolean success = false;
    try {
      /*
       * contentType = "text/plain"
       * contentType = "text/html"
       * contentType = "application/pdf"
       * contentType = "image/png"
       */
      FromEmail consultFromEmail = ServiceConfigs.getFromEmail();
      Email from = new Email(consultFromEmail.getMail(), consultFromEmail.getName(langCode));
      Email replyTo = new Email(consultFromEmail.getMail(), consultFromEmail.getName(langCode));
      Email toObj = new Email(to, toName);
      Content contentObj = new Content(contentType, content);
      Mail mail = new Mail(from, subject, toObj, contentObj);
      mail.setReplyTo(replyTo);
      Request request = new Request();
      request.setMethod(Method.POST);
      request.setEndpoint("mail/send");
      request.setBody(mail.build());
      LOGGER.info("\"sendGridRequest.body\": " + request.getBody());
      Response response = sendGrid.api(request);
      if (SUCCESS_CODES.contains(response.getStatusCode())) {
        success = true;
      }
      LOGGER.info("\"sendGridResponse.statusCode\": " + response.getStatusCode());
      String responseHeader = mapper.writeValueAsString(response.getHeaders());
      LOGGER.info("\"sendGridResponse.header\": " + responseHeader);
      LOGGER.info("\"sendGridResponse.body\": " + response.getBody());
    } catch (Exception e) {
      LOGGER.error(e);
    }
    return success;
  }

  public static boolean validateEmail(String emailStr) {
    boolean result = false;
    if (StringUtils.isNotEmpty(emailStr)) {
      result = emailStr.matches(VALID_EMAIL_ADDRESS_REGEX);
    }
    return result;
  }
}
