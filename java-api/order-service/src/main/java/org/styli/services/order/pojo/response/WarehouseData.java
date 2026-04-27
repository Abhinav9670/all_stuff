package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class WarehouseData {
	private String id;
	private String name_en;
	private String name_ar;
	private boolean enabled;
	private int min_sla;
	private int max_sla;
	private boolean cod_verification;
	private int threshold;
	private int max_threshold;
	private String type;
	private boolean fast_delivery;
	private boolean fast_delivery_eligible;
	private boolean cutoff_time_excluded;
	private boolean is_payment_auto_refunded;
	private String cut_off_time;
	private String holiday;
	private String country;
	private String province_id;

	private String estimated_date_min;
	private String estimated_date_max;
	private String estimated_date;

	private String estimated_date_tostring_en;
	private String estimated_date_tostring_ar;
	private String estimated_date_max_tostring_en;
	private String estimated_date_max_tostring_ar;
	private String estimated_date_min_tostring_en;
	private String estimated_date_min_tostring_ar;
}

