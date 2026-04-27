package org.styli.services.customer.utility.pojo.config;
 
import java.io.Serializable;
import java.util.List;
 
import lombok.Data;
 
@Data
public class PhoneNumberValidation implements Serializable{
 
 private static final long serialVersionUID = -1839480363033455681L;
private Integer maxLength;
 private Integer actualLength;
 private String lableHintNumber;
 private List<Validation> validation;
}