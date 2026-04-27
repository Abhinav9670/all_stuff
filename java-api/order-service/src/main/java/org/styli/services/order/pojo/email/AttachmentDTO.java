package org.styli.services.order.pojo.email;

import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;

@Getter
@Setter
public class AttachmentDTO {

	private InputStream attachmentStream;
	
	private String filename;
	
	private String mimeType;

}
