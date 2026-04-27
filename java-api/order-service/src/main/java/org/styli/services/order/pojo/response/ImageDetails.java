package org.styli.services.order.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ImageDetails implements Serializable {

    private static final long serialVersionUID = 9119852040944994787L;

    private String image;

    private String smallImage;

    private String thumbnail;

    private String image2;

    private String smallImage2;

    private String thumbnail2;

    private List<String> mediaGallery;


}
