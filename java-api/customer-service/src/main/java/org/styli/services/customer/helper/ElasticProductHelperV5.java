package org.styli.services.customer.helper;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.customer.client.OrderClient;
import org.styli.services.customer.pojo.*;
import org.styli.services.customer.pojo.elastic.*;
import org.styli.services.customer.pojo.registration.response.Product.FlashSaleV4DTO;
import org.styli.services.customer.pojo.registration.response.Product.KeyValuePair;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.styli.services.customer.pojo.registration.response.Product.ProductValue;
import org.styli.services.customer.repository.StaticComponents;

import java.util.*;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Component
public class ElasticProductHelperV5 {


    private static final String PUB = "/pub/";

	@Autowired
    OrderClient orderClient;

    @Value("${magento.base.url}")
    private String magentoBaseUrl;

    public ProductDetailsResponseV4DTO getDetailedProduct(ResponseDetailElastic elasticResponse,
                                                          HitsDetailResponseElastic hit, Integer storeId,
                                                          Map<String, String> productIdWishlistItemIdMap) {
        ProductDetailsResponseV4DTO dto = null;


        if(elasticResponse!=null && hit!=null && storeId != null) {
            dto = new ProductDetailsResponseV4DTO();
            dto.setId(hit.getObjectID());
            dto.setSku((CollectionUtils.isNotEmpty(hit.getSku()))?hit.getSku().get(0):null);
            dto.setName(hit.getName());
            dto.setMetaTitle(hit.getMetaTitle());
            dto.setUrl(hit.getUrl());
            dto.setMetaDescription(hit.getMetaDescription());
            dto.setProductType(hit.getTypeId());
            dto.setIsVisibleProduct(!((hit.getIsDisabled()==null) ? false : hit.getIsDisabled()));
            dto.setBrand(hit.getBrandName());
            dto.setPromoBadgeText(hit.getPromoBadge());
            dto.setPromoBgColor(hit.getPromoBgColor());
            dto.setPromoTextColor(hit.getPromoTextColor());
            if(StringUtils.isNotEmpty(hit.getObjectID())) {
                dto.setWishListItemId(productIdWishlistItemIdMap.get(dto.getSku()));
            }
            dto.setReturnCategoryRestriction(
                    !(hit.getIsReturnApplicable()!=null && hit.getIsReturnApplicable().equals(1)));

            dto.setCategories(hit.getCategories());

            /*
             * Set Image and Media Image
             */
            setImageDetails(dto, hit);

            /*
             * Set ProductFilterAttributes
             */
         //   setProductFilterAttributes(dto, hit);
            /*
             * Set price details and currency
             */
            setPriceDetails(dto, hit);
            /*
             * Set child Products
             */
            setChildProducts(dto, hit);

            setFlashSale(dto, elasticResponse, hit);

            dto.setSource(null);
            dto.setPriceSource("VM");
        }

        return dto;
    }
    private ProductDetailsResponseV4DTO setImageDetails(
            ProductDetailsResponseV4DTO dto, HitsDetailResponseElastic hit) {
        if(dto != null && hit != null) {
            ImageDetails imageDetails = new ImageDetails();
            imageDetails.setImage(convertMediaUrl(hit.getImageUrl()));
            imageDetails.setThumbnail(convertMediaUrl(hit.getThumbnailUrl()));
            List<String> mediaGallery = new ArrayList<>();
            if(CollectionUtils.isNotEmpty(hit.getMediaGallery())) {
                for (String item: hit.getMediaGallery()) {
                    if(item != null) {
                        mediaGallery.add(convertMediaUrl(item));
                    }
                }
            }
            imageDetails.setMediaGallery(mediaGallery);
            dto.setImages(imageDetails);
        }
        return dto;
    }

    private ProductDetailsResponseV4DTO setProductFilterAttributes(
            ProductDetailsResponseV4DTO dto, HitsDetailResponseElastic hit) {
        if(dto != null && hit != null) {
            Map<String, AttributeValue> attrs = orderClient.getAttrStatusMap();
            Map<String, List<KeyValuePair>> productFilterAttributes = new LinkedHashMap<>();
            processAttributes(hit, attrs, productFilterAttributes);
            dto.setProductFilterAttributes(productFilterAttributes);
        }
        return dto;
    }
	private void processAttributes(HitsDetailResponseElastic hit, Map<String, AttributeValue> attrs,
			Map<String, List<KeyValuePair>> productFilterAttributes) {
		if(MapUtils.isNotEmpty(hit.getProductAttributeFilters())) {
		    Map<String, ProductAttributeElastic> attrFilters = hit.getProductAttributeFilters();

		    List<Map.Entry<String, ProductAttributeElastic>> attributeEntries = Arrays.asList(
		            attrFilters.entrySet().toArray(new Map.Entry[attrFilters.size()]));;
		    for (Map.Entry<String, ProductAttributeElastic> entry: attributeEntries) {
		        if(entry != null && entry.getKey() != null && entry.getValue() != null) {
		            setPdpVisibleAttribute(attrs, productFilterAttributes, entry);
		        }
		    }
		}
	}
	private void setPdpVisibleAttribute(Map<String, AttributeValue> attrs,
			Map<String, List<KeyValuePair>> productFilterAttributes, Map.Entry<String, ProductAttributeElastic> entry) {
		String filterAttributeKey = entry.getKey();
		filterAttributeKey = filterAttributeKey + "_" + "4";
		if(!entry.getKey().equalsIgnoreCase("fabric_1")
		        && MapUtils.isNotEmpty(attrs) && null != attrs.get(filterAttributeKey)
		        && StringUtils.isNotBlank(attrs.get(filterAttributeKey).getAttributeType())
		        && attrs.get(filterAttributeKey).getPdpVisiable().equals(1)) {
		    AttributeValue attributeValue = attrs.get(filterAttributeKey);
		    if(attributeValue!=null
		            && attributeValue.getPdpVisiable()!=null
		            && attributeValue.getPdpVisiable().equals(1)) {
		        ProductAttributeElastic value = entry.getValue();
		        List<KeyValuePair> item = new ArrayList<>();
		        KeyValuePair keyValuePair = new KeyValuePair();
		        keyValuePair.setName(value.getName());
		        item.add(keyValuePair);
		        productFilterAttributes.put(value.getLabel(), item);
		    }
		}
	}

    private ProductDetailsResponseV4DTO setPriceDetails(
            ProductDetailsResponseV4DTO dto, HitsDetailResponseElastic hit) {
        if(dto != null && hit != null && MapUtils.isNotEmpty(hit.getPrice())) {
            Map.Entry<String, PriceType>[] prices = new Map.Entry[hit.getPrice().entrySet().size()];
            prices = hit.getPrice().entrySet().toArray(prices);
            processPriceDetails(dto, hit, prices);
        }
        return dto;
    }
	private void processPriceDetails(ProductDetailsResponseV4DTO dto, HitsDetailResponseElastic hit,
			Map.Entry<String, PriceType>[] prices) {
		if(prices[0] != null) {
		    Map.Entry<String, PriceType> priceEntry = prices[0];
		    dto.setCurrency(priceEntry.getKey());
		    PriceDetails priceDetails = new PriceDetails();
		    PriceType priceType = priceEntry.getValue();
		    calculatePriceDetails(dto, hit, priceEntry, priceDetails, priceType);

		    dto.setPrices(priceDetails);
		}
	}
	private void calculatePriceDetails(ProductDetailsResponseV4DTO dto, HitsDetailResponseElastic hit,
			Map.Entry<String, PriceType> priceEntry, PriceDetails priceDetails, PriceType priceType) {
		if(priceType!=null) {
		    Double price, specialPrice;
		    price = (priceType.getDefaultOriginalFormatted() != null)
		            ?Double.parseDouble(priceType.getDefaultOriginalFormatted()
		            .replace(priceEntry.getKey(), "").trim()): priceType.getDefaultPrice();
		    specialPrice = (price!=null && ! price.equals(priceType.getDefaultPrice()))
		            ? priceType.getDefaultPrice(): null;

		    priceDetails.setPrice((price!=null)?price.toString():"");
		    priceDetails.setSpecialPrice((specialPrice!=null)?specialPrice.toString():"");
		    dto.setDiscount(hit.getDiscountPercentage());
		}
	}

    private ProductDetailsResponseV4DTO setChildProducts(
            ProductDetailsResponseV4DTO dto, HitsDetailResponseElastic hit) {
        if(dto != null && hit != null && CollectionUtils.isNotEmpty(hit.getConfigProducts())) {
            List<ProductDetailsResponseV4DTO> children = new ArrayList<>();
            for (ChildProductElastic child : hit.getConfigProducts()) {
                if(child!=null) {
                    ProductDetailsResponseV4DTO childDto = new ProductDetailsResponseV4DTO();
                    childDto.setId(child.getId());
                    childDto.setSku(child.getSku());
                    childDto.setSize(child.getSize());
                    childDto.setIsVisibleProduct(true);
                    children.add(childDto);
                }
            }
            dto.setConfigProducts(children);
        }
        return dto;
    }

    private ProductDetailsResponseV4DTO setFlashSale(
            ProductDetailsResponseV4DTO dto, ResponseDetailElastic elasticResponse, HitsDetailResponseElastic hit) {
        if(dto != null && elasticResponse != null && hit != null && elasticResponse.getFlashSale() != null) {
            FlashSaleV4DTO flashSaleV4DTO = new FlashSaleV4DTO();
            flashSaleV4DTO.setActive(elasticResponse.getFlashSale().getActive());
            flashSaleV4DTO.setStart(elasticResponse.getFlashSale().getStart());
            flashSaleV4DTO.setEnd(elasticResponse.getFlashSale().getEnd());
            flashSaleV4DTO.setColor(elasticResponse.getFlashSale().getColor());
            dto.setIsFlashSale(hit.getFlashSale());
            dto.setFlashSale(flashSaleV4DTO);
        }
        return dto;
    }

    private String convertMediaUrl(String input) {
        String result = input;
        if(input!=null) {
            if(input.contains(PUB)) {
                List<String> chunk = new ArrayList<>(Arrays.asList(input.split(PUB)));
                if(chunk.size()>1)
                    chunk.remove(0);
                String path = String.join(PUB, chunk.toArray(new String[chunk.size()]));
                result = ((magentoBaseUrl!=null)? magentoBaseUrl : "")+ "/" + path;
            } else if(input.startsWith("//")) {
                result = "https:"+input;
            }
        }
        return result;
    }
}
