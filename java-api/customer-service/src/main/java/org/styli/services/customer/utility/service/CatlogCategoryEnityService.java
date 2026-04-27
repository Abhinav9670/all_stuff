package org.styli.services.customer.utility.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.styli.services.customer.utility.exception.CustomerException;
import org.styli.services.customer.utility.pojo.category.CategoryListResponse;

@Service
public interface CatlogCategoryEnityService {
	CategoryListResponse findAllCategories(Map<String, String> requestHeader, Integer storeId, boolean toPush) throws CustomerException;
}
