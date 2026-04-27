package org.styli.services.order.pojo.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Getter
@AllArgsConstructor
public enum PaymentCodeENUM {

    // For backward compatibility, existing payfort paymentCode is kept
	CASH_ON_DELIVERY("cashondelivery"), PAYFORT_FORT_CC("payfort_fort_cc"), MD_PAYFORT_CC_VAULT("md_payfort_cc_vault"),
	MD_PAYFORT("md_payfort"), FREE("free"), APPLE_PAY("apple_pay"), TABBY_IMSTALLMENTS("tabby_installments"),
	TABBY_PAYLATER("tabby_paylater"),SHUKRAN_PAYMENT("shukran_payment");

    private String value;

}
