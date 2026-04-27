package org.styli.services.order.service;

import com.sendgrid.Response;

public interface EmailService {

    public Response sendTextWithAttachment(String to, String subject, String body, String fileName);

    public Response sendText(String to, String subject, String body);

    public Response sendHTML(String to, String subject, String body);

}
