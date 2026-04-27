package org.styli.services.order.pojo.tax;

import lombok.Data;

@Data 
public class TaxObject {
	
	/** India tax Params */
    private String taxIGST;
    
    private String taxIGSTAmount;
    
    private String taxCGST;
    
    private String taxCGSTAmount;

    private String taxSGST;
    
    private String taxSGSTAmount;

    private boolean intraState;
    /** India tax Params */

}
