package org.styli.services.customer.pojo.account;

import java.io.Serializable;

import lombok.Data;

@Data
public class StyliCoinsResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private Integer code;

	private String status;

	private StyliCoinsData data;
}
