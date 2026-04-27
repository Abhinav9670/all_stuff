package org.styli.services.customer.pojo.registration.response.Product;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class KeyValuePair implements Comparable<KeyValuePair>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String name;

	private String value;

	private String count;

	private String frontInputType;

	private String attributeCode;

	private String colorHash;

	private Set<Integer> productIdList;

	// @JsonIgnore
	private List<Integer> subCategoryProductIds;

	/** This is internally used to store the subcategory product ids **/

	@Override
	public int compareTo(KeyValuePair object) {
		// TODO Auto-generated method stub
		return name.compareTo(object.name);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		KeyValuePair other = (KeyValuePair) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}
}
