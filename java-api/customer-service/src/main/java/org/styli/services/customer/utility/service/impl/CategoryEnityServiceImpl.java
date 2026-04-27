package org.styli.services.customer.utility.service.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.customer.utility.client.OrderClient;
import org.styli.services.customer.utility.exception.CustomerException;
import org.styli.services.customer.utility.pojo.ErrorType;
import org.styli.services.customer.utility.pojo.category.Category;
import org.styli.services.customer.utility.pojo.category.CategoryListResponse;
import org.styli.services.customer.utility.pojo.category.CategoryResponseBody;
import org.styli.services.customer.utility.pojo.category.FirstSubCategory;
import org.styli.services.customer.utility.pojo.category.MagentoCategoryListRes;
import org.styli.services.customer.utility.pojo.category.MagentoSubCategoryRes;
import org.styli.services.customer.utility.pojo.category.MagentoSuperSubCategoryRes;
import org.styli.services.customer.utility.pojo.category.MagentoSuperSubTypeCategoryRes;
import org.styli.services.customer.utility.pojo.category.SecondSubCategory;
import org.styli.services.customer.utility.pojo.category.ThirdSubCategory;
import org.styli.services.customer.utility.service.CatlogCategoryEnityService;
import org.styli.services.customer.utility.utility.UtilityConstant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Component
public class CategoryEnityServiceImpl implements CatlogCategoryEnityService {
  private static final Log LOGGER = LogFactory.getLog(CategoryEnityServiceImpl.class);

  @Autowired
  OrderClient orderClient;

  // @Value("${gcp.service.account.key.path}")
  // String gcpServiceKeyPath;

  @Value("${gcp.project.id}")
  String gcpProjectId;

  @Value("${gcp.bucket.name}")
  String gcpBucketName;

  @Value("${gcp.category.name}")
  String gcpObjectName;

  @Value("${gcp.certificate.path}")
  String gcpCertificatePath;

  @Value("${env}")
  String env;

  @Override
  @Transactional(readOnly = true)
  public CategoryListResponse findAllCategories(Map<String, String> requestHeader, Integer storeId, boolean toPush) {

    CategoryListResponse response = new CategoryListResponse();

    try {

      MagentoCategoryListRes magentoResponse = orderClient.getAllCategories(requestHeader, storeId);

      if (null != magentoResponse && !CollectionUtils.isEmpty(magentoResponse.getChildren_data())) {

        CategoryResponseBody responseBody = new CategoryResponseBody();
        List<Category> categories = new ArrayList<>();

        for (MagentoSubCategoryRes mainSubCategory : magentoResponse.getChildren_data()) {// WOMEN
                                                                                          // Category

          if (null != mainSubCategory.getInclude_in_menu() && mainSubCategory.getInclude_in_menu()) {

            Category mainCategory = new Category();

            mainCategory.setId(mainSubCategory.getId());
            mainCategory.setParentId(mainSubCategory.getParent_id());
            mainCategory.setName(mainSubCategory.getName());
            mainCategory.setIsActive(mainSubCategory.getIs_active());
            mainCategory.setPosition(mainSubCategory.getPosition());
            mainCategory.setLevel(mainSubCategory.getLevel());
            mainCategory.setProductCount(mainSubCategory.getProduct_count());
            mainCategory.setCategoryKey(mainSubCategory.getName());
            mainCategory.setMagentoPathUrl(mainSubCategory.getMagento_path_url());
            // String imageUrl =
            // catlogCategoryEnityRepository.getCategoryImage(mainSubCategory.getId());
            // if(StringUtils.isNotBlank(imageUrl)) {
            //
            // mainCategory.setImageUrl(configProperties.getMagentoCategoryImageUrl()+imageUrl);
            //
            // }
            mainCategory.setImageUrl(mainSubCategory.getThumbnail());
            mainCategory.setMetaTitle(mainSubCategory.getMeta_title());
            mainCategory.setMetaKeywords(mainSubCategory.getMeta_keywords());
            mainCategory.setMetaDescription(mainSubCategory.getMeta_description());

            List<FirstSubCategory> firstSubCategoryList = new ArrayList<>();

            for (MagentoSuperSubCategoryRes mainSubChildCategory : mainSubCategory.getChildren_data()) { // NIghtWare,
                                                                                                         // Clothing

              if (null != mainSubChildCategory && mainSubChildCategory.getInclude_in_menu()) {

                FirstSubCategory firstSubCategory = new FirstSubCategory();

                firstSubCategory.setId(mainSubChildCategory.getId());
                firstSubCategory.setParentId(mainSubChildCategory.getParent_id());
                firstSubCategory.setName(mainSubChildCategory.getName());
                firstSubCategory.setIsActive(mainSubChildCategory.getIs_active());
                firstSubCategory.setPosition(mainSubChildCategory.getPosition());
                firstSubCategory.setLevel(mainSubChildCategory.getLevel());
                firstSubCategory.setProductCount(mainSubChildCategory.getProduct_count());
                firstSubCategory.setCategoryKey(new StringBuilder().append(mainSubCategory.getName())
                    .append(UtilityConstant.SUBCATEGORIES_DELIMETER).append(mainSubChildCategory.getName()).toString());
                firstSubCategory.setMagentoPathUrl(mainSubChildCategory.getMagento_path_url());
                // String firstCategoryImageUrl =
                // catlogCategoryEnityRepository.getCategoryImage(mainSubChildCategory.getId());
                // if(StringUtils.isNotBlank(firstCategoryImageUrl)) {
                //
                // firstSubCategory.setImageUrl(configProperties.getMagentoCategoryImageUrl()+firstCategoryImageUrl);
                //
                // }
                firstSubCategory.setImageUrl(mainSubChildCategory.getThumbnail());
                firstSubCategory.setMetaTitle(mainSubChildCategory.getMeta_title());
                firstSubCategory.setMetaKeywords(mainSubChildCategory.getMeta_keywords());
                firstSubCategory.setMetaDescription(mainSubChildCategory.getMeta_description());

                List<SecondSubCategory> secondSubCategoryList = new ArrayList<>();

                for (MagentoSuperSubTypeCategoryRes mainSuperSubCategory : mainSubChildCategory.getChildren_data()) { // Abayas,
                                                                                                                      // Bottom
                                                                                                                      // Wear

                  if (null != mainSuperSubCategory && mainSuperSubCategory.getInclude_in_menu()) {

                    SecondSubCategory secondSubCategory = new SecondSubCategory();

                    secondSubCategory.setId(mainSuperSubCategory.getId());
                    secondSubCategory.setParentId(mainSuperSubCategory.getParent_id());
                    secondSubCategory.setName(mainSuperSubCategory.getName());
                    secondSubCategory.setIsActive(mainSuperSubCategory.getIs_active());
                    secondSubCategory.setPosition(mainSuperSubCategory.getPosition());
                    secondSubCategory.setLevel(mainSuperSubCategory.getLevel());
                    secondSubCategory.setProductCount(mainSuperSubCategory.getProduct_count());
                    secondSubCategory.setCategoryKey(new StringBuilder().append(mainSubCategory.getName())
                        .append(UtilityConstant.SUBCATEGORIES_DELIMETER).append(mainSubChildCategory.getName())
                        .append(UtilityConstant.SUBCATEGORIES_DELIMETER).append(mainSuperSubCategory.getName())
                        .toString());
                    // String secondCategoryImageUrl =
                    // catlogCategoryEnityRepository
                    // .getCategoryImage(mainSuperSubCategory.getId());
                    secondSubCategory.setMagentoPathUrl(mainSuperSubCategory.getMagento_path_url());
                    // if
                    // (StringUtils.isNotBlank(secondCategoryImageUrl))
                    // {
                    //
                    // secondSubCategory.setImageUrl(configProperties.getMagentoCategoryImageUrl()
                    // + secondCategoryImageUrl);
                    //
                    // }
                    secondSubCategory.setImageUrl(mainSuperSubCategory.getThumbnail());
                    secondSubCategory.setMetaTitle(mainSuperSubCategory.getMeta_title());
                    secondSubCategory.setMetaKeywords(mainSuperSubCategory.getMeta_keywords());
                    secondSubCategory.setMetaDescription(mainSuperSubCategory.getMeta_description());

                    List<ThirdSubCategory> thirdSubCategoryList = new ArrayList<>();

                    for (MagentoSuperSubTypeCategoryRes superSubTypeChildrenCategory : mainSuperSubCategory
                        .getChildren_data()) {

                      if (null != superSubTypeChildrenCategory && superSubTypeChildrenCategory.getInclude_in_menu()) {

                        ThirdSubCategory thirdSubCategory = new ThirdSubCategory();

                        thirdSubCategory.setId(superSubTypeChildrenCategory.getId());
                        thirdSubCategory.setParentId(superSubTypeChildrenCategory.getParent_id());
                        thirdSubCategory.setName(superSubTypeChildrenCategory.getName());
                        thirdSubCategory.setIsActive(superSubTypeChildrenCategory.getIs_active());
                        thirdSubCategory.setPosition(superSubTypeChildrenCategory.getPosition());
                        thirdSubCategory.setLevel(superSubTypeChildrenCategory.getLevel());
                        thirdSubCategory.setProductCount(superSubTypeChildrenCategory.getProduct_count());
                        thirdSubCategory.setCategoryKey(new StringBuilder().append(mainSubCategory.getName())
                            .append(UtilityConstant.SUBCATEGORIES_DELIMETER).append(mainSubChildCategory.getName())
                            .append(UtilityConstant.SUBCATEGORIES_DELIMETER).append(mainSuperSubCategory.getName())
                            .append(UtilityConstant.SUBCATEGORIES_DELIMETER)
                            .append(superSubTypeChildrenCategory.getName()).toString());
                        thirdSubCategory.setMagentoPathUrl(superSubTypeChildrenCategory.getMagento_path_url());
                        // String
                        // thirsdCategoryImageUrl
                        // =
                        // catlogCategoryEnityRepository
                        // .getCategoryImage(superSubTypeChildrenCategory.getId());
                        //
                        // if
                        // (StringUtils.isNotBlank(thirsdCategoryImageUrl))
                        // {
                        //
                        // thirdSubCategory
                        // .setImageUrl(configProperties.getMagentoCategoryImageUrl()
                        // +
                        // thirsdCategoryImageUrl);
                        //
                        // }
                        thirdSubCategory.setImageUrl(superSubTypeChildrenCategory.getThumbnail());
                        thirdSubCategory.setMetaTitle(superSubTypeChildrenCategory.getMeta_title());
                        thirdSubCategory.setMetaKeywords(superSubTypeChildrenCategory.getMeta_keywords());
                        thirdSubCategory.setMetaDescription(superSubTypeChildrenCategory.getMeta_description());

                        thirdSubCategoryList.add(thirdSubCategory);
                      }
                    }

                    secondSubCategory.setSubCategories(thirdSubCategoryList);

                    secondSubCategoryList.add(secondSubCategory);

                  }
                }
                firstSubCategory.setSubCategories(secondSubCategoryList);

                firstSubCategoryList.add(firstSubCategory);

              }
            }

            mainCategory.setSubCategories(firstSubCategoryList);

            categories.add(mainCategory);

          }
        }

        responseBody.setCategories(categories);

        response.setResponse(responseBody);
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("Fetched Successfully !!");
        if (toPush) {
          saveDataToGCP(response, storeId);
        }
      }

    } catch (CustomerException e) {

      ErrorType error = new ErrorType();
      error.setErrorCode(e.getErrorCode());
      error.setErrorMessage(e.getErrorMessage().toString());
      response.setStatus(false);
      response.setStatusCode("204");
      response.setStatusMsg("ERROR !!");
      response.setError(error);
    } catch (Exception e) {
      LOGGER.error("GCP_EXCEPTION", e);
    }

    return response;
  }

  // Categopry save to gcp
  public void saveDataToGCP(CategoryListResponse resp, Integer storeId) {
    Bucket bucket = null;
    Storage storage = null;
    try {
      Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(gcpCertificatePath));
      storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(gcpProjectId).build().getService();
      bucket = storage.get(gcpBucketName);

      ObjectMapper Obj = new ObjectMapper();
      String jsonStr = Obj.writeValueAsString(resp);
      byte[] bytes = jsonStr.getBytes(UTF_8);

      Map<String, String> newMetadata = new HashMap<>();
      newMetadata.put("Cache-Control", "public,max-age=1800");

      if (bucket != null) {
        String fileName = gcpObjectName + "-" + storeId + ".json";
        String fullPath = env + "/" + fileName;
        Blob blob = storage.get(gcpBucketName, fullPath);
        if (blob != null) {
          if (blob.exists()) {
            blob.toBuilder().setMetadata(newMetadata).build().update();
            WritableByteChannel channel = blob.writer();
            channel.write(ByteBuffer.wrap(bytes));
            channel.close();
          } else {
            bucket.create(fullPath, bytes, "application/json");
            storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
          }
        } else {
          bucket.create(fullPath, bytes, "application/json");
          storage.get(gcpBucketName, fullPath).toBuilder().setMetadata(newMetadata).build().update();
        }
      }

    } catch (Exception e) {
      LOGGER.error("GCP_EXCEPTION", e);
    }

  }
}
