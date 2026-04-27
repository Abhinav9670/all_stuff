package org.styli.services.order.pojo;

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

    public static CustomerStatus findByAbbr(Integer abbr){
        for(CustomerStatus v : values()){
            if( v.value.equals(abbr)){
                return v;
            }
        }
        return null;
    }

}
