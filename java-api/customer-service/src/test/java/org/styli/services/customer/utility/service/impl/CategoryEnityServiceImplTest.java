package org.styli.services.customer.utility.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.styli.services.customer.utility.client.OrderClient;
import org.styli.services.customer.utility.exception.CustomerException;
import org.styli.services.customer.utility.pojo.category.CategoryListResponse;
import org.styli.services.customer.utility.pojo.category.MagentoCategoryListRes;
import org.styli.services.customer.utility.pojo.category.MagentoSubCategoryRes;
import org.styli.services.customer.utility.pojo.category.MagentoSuperSubCategoryRes;
import org.styli.services.customer.utility.pojo.category.MagentoSuperSubTypeCategoryRes;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class CategoryEnityServiceImplTest {
	@InjectMocks
	CategoryEnityServiceImpl categoryEnityServiceImplTest;
	Map<String, String> requestHeader;
	@Mock
	OrderClient orderClient;

	@BeforeClass
	public void beforeClass() {

		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");

	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void findAllCategoriesTest() throws CustomerException {

		MagentoSuperSubTypeCategoryRes magentoSuperSubTypeCategoryRes = new MagentoSuperSubTypeCategoryRes();
		magentoSuperSubTypeCategoryRes.setId(4);
		magentoSuperSubTypeCategoryRes.setInclude_in_menu(true);
		magentoSuperSubTypeCategoryRes.setChildren_data(java.util.Arrays.asList(magentoSuperSubTypeCategoryRes));
		MagentoSuperSubCategoryRes supersubcate = new MagentoSuperSubCategoryRes();
		supersubcate.setId(3);
		supersubcate.setInclude_in_menu(true);
		supersubcate.setChildren_data(java.util.Arrays.asList(magentoSuperSubTypeCategoryRes));
		MagentoSubCategoryRes subcate = new MagentoSubCategoryRes();
		subcate.setInclude_in_menu(true);
		subcate.setId(2);
		subcate.setChildren_data(java.util.Arrays.asList(supersubcate));
		List<MagentoSubCategoryRes> lst = java.util.Arrays.asList(subcate);
		MagentoCategoryListRes repo = new MagentoCategoryListRes();
		repo.setId(1);
		repo.setChildren_data(lst);
		when(orderClient.getAllCategories(any(), anyInt())).thenReturn(repo);
		CategoryListResponse reponse = categoryEnityServiceImplTest.findAllCategories(requestHeader, 1, false);
		assertEquals(reponse.isStatus(), true);
		assertNotNull(reponse.getResponse());
	}
}
