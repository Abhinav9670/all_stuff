package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Created on 12-Oct-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class CategoryUrlPathRequest implements Serializable {

    private static final long serialVersionUID = -7805060394007887456L;
    @NotNull
    private String categoryPath;

    private String langCode;
}
