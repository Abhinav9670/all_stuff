package org.styli.services.customer.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.styli.services.customer.pojo.registration.response.ErrorType;

/**
 * Created on 05-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenericApiResponse<T> {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private ErrorType error;
    private T response;
}
