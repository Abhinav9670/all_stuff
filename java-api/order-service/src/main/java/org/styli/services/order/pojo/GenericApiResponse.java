package org.styli.services.order.pojo;

import java.util.Map;

import org.styli.services.order.pojo.whatsapp.bot.MobileReturnDetailResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created on 27-Oct-2022
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
    private int returnCount;
    private Map<String, MobileReturnDetailResponse> responses;
}
