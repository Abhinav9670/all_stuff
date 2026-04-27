package org.styli.services.order.model.Product;

//@SqlResultSetMapping(

//	    name = "findAllDataMapping",
//	    classes = @ConstructorResult(
//	            targetClass = ProductDetailDTO.class,
//	            columns = {
//	                    @ColumnResult(name = "productName" ,type=String.class),
//	                    @ColumnResult(name = "value" , type=String.class),
//	                    @ColumnResult(name = "sku" ,  type=String.class)
//	                 
//	            }
//	            
//	    )
//	)

//
//	@NamedNativeQuery(name = "findAllDataMapping",
//	    query = "SELECT nametable.value as productName,\n" + 
//	    		"      nametable.store_id  as value,\n" + 
//	    		"      catalog_product_entity.sku as sku\n" + 
//	    		"FROM   catalog_product_entity_varchar AS nametable\n" + 
//	    		"      LEFT JOIN catalog_product_entity\n" + 
//	    		"             ON nametable.entity_id = catalog_product_entity.entity_id\n" + 
//	    		"WHERE  nametable.attribute_id = (SELECT attribute_id\n" + 
//	    		"                                FROM   eav_attribute\n" + 
//	    		"                                WHERE  entity_type_id = 4\n" + 
//	    		"                                       AND attribute_code LIKE 'name')", resultClass = ProductDetailDTO.class,
//	    		resultSetMapping ="findAllDataMapping"
//	)

//@Entity
//@Table(name = "ProductDetailDTO")

//@Data
public class ProductDetailDTO {

	private String productName;
	private int value;
	private String sku;

	public ProductDetailDTO() {
		// super();
	}

	public ProductDetailDTO(String productName, int value, String sku) {
		// super();
		this.productName = productName;
		this.value = value;
		this.sku = sku;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
	}

}
