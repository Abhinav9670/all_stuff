package org.styli.services.customer.pojo.consul;

import lombok.Data;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 10/06/2022 - 3:31 PM
 */

@Data
public class Email {
    private Message subject;
    private Message content;
}
