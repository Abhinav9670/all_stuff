package org.styli.services.customer.pojo.account;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CustomerStatus {

    DELETE_REQUESTED(0),
    ACTIVE(1),
    DELETED(2),
    DISABLED(3);

    private Integer value;

}
