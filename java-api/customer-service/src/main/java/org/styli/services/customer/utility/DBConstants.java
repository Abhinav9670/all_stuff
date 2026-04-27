package org.styli.services.customer.utility;

public enum DBConstants {

	// SIZE_ID(142),
	TEXT_ID("DESCRIPTION"), SIZE_FIT("SIZE_FIT"), NAME_ID("NAME"), CAT_NAME_ID("NAME"), IMAGE_ID("IMAGE"),
	THUMBNAIL_ID("THUMBNAIL"), SMALLIMAGE_ID("SMALL_IMAGE"), PRICE_ID("PRICE"), SPECIAL_PRICE_ID("SPECIAL_PRICE"),
	// COLOR_ID(56),
	PRODUCT_NOT_ENABLE("STATUS"), ATTR_COLOR("COLOR"), PRODUCT_ENABALE("STATUS"),
	ATTR_QTYSTOCK("quantity_and_stock_status"), ATTR_TAXCLASS("tax_class_id"), ATTR_SIZE("size"), STORE_ID_0("0"),
	STORE_ID_1("1"), PRODUCT_SEARCH_VISIBLE_4("4"), PRODUCT_SEARCH_VISIBLE_3("3"), PRODUCT_SEARCH_VISIBLE_2("2"),
	PRODUCT_SEARCH_VISIBLE_1("1"), PRODUCT_VISIBLE("VISIBILITY"), SUBCATEGORY_TYPE("subCategories"),
	MEDIA_GALLERY("IMAGE"), INVENTORY_STOCK_ID("1"), BRAND_NAME("brand_name"), IS_ACTIVE("IS_ACTIVE");

	public String value;

	DBConstants(String value) {

		this.value = value;
	}

	public String getValue() {

		return value;
	}

}
