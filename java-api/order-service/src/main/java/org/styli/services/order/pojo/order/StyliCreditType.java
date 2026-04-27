package org.styli.services.order.pojo.order;

import java.io.Serializable;


public enum StyliCreditType implements Serializable {

	REFERRAL("referral"),
	BANK_TRANSFER("bank_transfer"),
	ADMIN_REFUND("admin_refund"),
	FINANCE_BULK_CHANGES("finance_bulk_changes"),
	CHANGED_BY_ADMIN("changhed_by_admin"),
	BLANK_ACTION("blank_action"),
	BRAZE_UPDATE("braze_update");
	

	  public String value;

	  StyliCreditType(String value) {

	    this.value = value;
	  }

	  public String getValue() {

	    return value;
	  }
	  
}