package org.styli.services.customer.service;

import com.sendgrid.Response;

public interface EmailService {
	
	public Response sendText(String to, String subject, String body);

}
