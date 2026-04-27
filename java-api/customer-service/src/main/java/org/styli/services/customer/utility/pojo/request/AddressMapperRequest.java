package org.styli.services.customer.utility.pojo.request;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * Created on 09-Dec-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */


@Data
public class AddressMapperRequest {

    @NotEmpty
    private String country;
}
