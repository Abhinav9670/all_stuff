package org.styli.services.order.utility;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public class UtilityConstant {

    public static String getColorName(String hexColor) {
        String name = "";
        switch (hexColor.toLowerCase()) {
            case "#ff0000":
                name = "Red";
                break;
            case "#00ff00":
                name = "Green";
                break;
            case "#1857f7":
                name = "Blue";
                break;
            case "#ffffff":
                name = "White";
                break;
            case "#000000":
                name = "Black";
                break;
            case "#53a828":
                name = "Green";
                break;
            case "#ffd500":
                name = "Yellow";
                break;
            case "#945454":
                name = "Brown";
                break;
            case "#8f8f8f":
                name = "Gray";
                break;
            case "#eb6703":
                name = "Orange";
                break;
            case "#ef3dff":
                name = "Purple";
                break;
            case "#0c6e3a":
                name = "Deep Green";
                break;
            case "#4287f5":
                name = "Light Blue";
                break;

            case "#242024":
                name = "Mardi Grass";
                break;

            case "#fa1ece":
                name = "Medium Spring Green";
                break;
            case "#ebfa1e":
                name = "Aqua";
                break;
            case "#000080":
                name = "Nevy Blue";
                break;
            case "#b89d16":
                name = "Lime";
                break;
            case "#2272ab":
                name = "Sky Blue";
                break;
            case "#ba4512":
                name = "Orange";
                break;
            case "#c43f1d":
                name = "Brown";
                break;
            case "#a31da3":
                name = "Violet";
                break;

            default:
                name = "Unknown";
                break;
        }
        return name;
    }

    public static final Integer GLOBAL_STORE_ID = 0;

    public static final Integer PRODUCT_IMAGE_ID = 87;
    public static final Integer PRODUCT_BRAND_ID = 482;
    public static final Integer PRODUCT_NAME_ID = 73;
    public static final String PRODUCT_SOLD_BY_ATTRIBUTE = "sold_by";

    public static final Integer STOCK_ID = 1;

    public static final String PRODCUT_TYPE_ID_CONNF = "configurable";

    public static final String PRODCUT_TYPE_ID_SIMEPLE = "simple";

    public static final String SUBCATEGORY_NAME = "categories";

    public static final String PRODCUT_COLOR_ATTR_TYPE_ENG = "color";

    public static final Integer APP_VERSION_NUMBER = 307;

    public static final String APP_HEADER_APP_VERSION = "x-client-version";
    public static final String APP_HEADER_APP_SOURCE = "x-source";

    public static final List<String> APPSOURCELIST = Collections.unmodifiableList(new ArrayList<String>() {
        /**
        * 
        */
        private static final long serialVersionUID = 1L;

        {
            add("ios");
            add("android");
            add("Android");
            add("iOS");
            add("Huawei");
            add("huawei");
            add("HUAWEI");
            add("msite");
            add("oldmsite");
        }
    });
    
    public static String ConvertTimeZone(Timestamp datetime, Integer storeId) {

		if (null != datetime) {
			Calendar calendar = Calendar.getInstance();

			Date dateTime = new Date(datetime.getTime());
			calendar.setTime(dateTime);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

			if (null != OrderConstants.timeZoneMap.get(storeId)) {
				sdf.setTimeZone(TimeZone.getTimeZone(OrderConstants.timeZoneMap.get(storeId)));
			} else {
				sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // TODO dynamic
			}

			return sdf.format(calendar.getTime());
		} else {

			return null;
		}

	}
    
    /**
	 * Convert an amount to two decimal place
	 * @param amount
	 * @return
	 */
	public static String amountToTwoDecimalPlace(BigDecimal amount) {
		DecimalFormat df = new DecimalFormat("###.##");
		return df.format(amount);
	}

}
