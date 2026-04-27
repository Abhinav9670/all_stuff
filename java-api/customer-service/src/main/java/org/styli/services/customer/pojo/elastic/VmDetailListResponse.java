package org.styli.services.customer.pojo.elastic;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VmDetailListResponse implements Serializable {

	private static final long serialVersionUID = -7597329078181936470L;
	private ProductDetailsList response;
	private boolean status;
	private String statusCode;
	private String statusMsg;
}
