package org.styli.services.order.pojo.whatsapp.bot;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * Created on 27-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class MobileOrderDetailRequest {

    @NotEmpty
    private String id;
}
