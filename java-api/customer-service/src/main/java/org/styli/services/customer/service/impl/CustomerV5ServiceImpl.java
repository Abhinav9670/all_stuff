package org.styli.services.customer.service.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.validation.Valid;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.customer.helper.ElasticProductHelperV5;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.pojo.*;
import org.styli.services.customer.pojo.elastic.HitsDetailResponseElastic;
import org.styli.services.customer.pojo.elastic.ProductAttributeElastic;
import org.styli.services.customer.pojo.elastic.ResponseDetailElastic;
import org.styli.services.customer.pojo.elastic.request.ProductListRequestV2;
import org.styli.services.customer.pojo.otp.CityBucketObject;
import org.styli.services.customer.pojo.registration.request.AccessTokenResponse;
import org.styli.services.customer.pojo.registration.request.CustomerWishlistV5Request;
import org.styli.services.customer.pojo.registration.request.GetProductV4Request;
import org.styli.services.customer.pojo.registration.request.LogoutResponse;
import org.styli.services.customer.pojo.registration.response.AccessTokenRequest;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.LoginHistory;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.styli.services.customer.pojo.registration.response.Product.ProductStatusResBody;
import org.styli.services.customer.pojo.registration.response.Product.ProductValue;
import org.styli.services.customer.pojo.response.CustomerOmsResponse;
import org.styli.services.customer.pojo.response.MulinProductDescRes;
import org.styli.services.customer.pojo.response.MulinProductDetails;
import org.styli.services.customer.pojo.response.ProductInfoValue;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.CustomerV5Service;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.UtilityCustomerConatant;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.utility.pojo.ErrorType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.styli.services.customer.utility.Constants.deviceId;

/**
 * @author Umesh, 07/07/2020
 * @project product-service
 */

@Component
public class CustomerV5ServiceImpl implements CustomerV5Service, ServiceConfigs.ServiceConfigsListener {

	private static final String LOCALITY = "locality";

	private static final String LATLNG = "?latlng=";

	private static final String PLACE_ID = "?place_id=";

	private static final String KEY = "&key=";

	private static final String CUSTOMER_ENTITY = "customer_entity";

	private static final String PHONE_NUMBER = "phoneNumber";

	private static final String FIRST_NAME = "firstName";

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomerV5ServiceImpl.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	Client client;

	@Value("${env}")
	private String env;

	@Value("${algolia.index.prefix}")
	private String algoliaPrefix;

	@Value("${vm.url}")
	private String vmUrl;

	@Autowired
	ElasticProductHelperV5 elasticProductHelperV5;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${customer.jwt.flag}")
	String jwtFlag;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	@Qualifier("gccMongoTemplate")
	private MongoTemplate mongoGccTemplate;

	@Autowired
	@Qualifier("indMongoTemplate")
	private MongoTemplate mongoInTemplate;

	@Autowired
	RedisHelper redisHelper;

	@Autowired
	GetWishlist getWishlist;

	@Autowired
	GetCustomer getCustomer;

	@Value("${mulin.url}")
	private String mulinUrl;

	@Value("${region}")
	private String region;

	private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	@Value("${google.maps.api.key}")
	private String googleMapsApiKey;

	@Value("${adrsmpr.base.url}")
	private String adrsmprBaseUrl;

	@Autowired
	StaticComponents staticComponents;
	
	@Autowired
	PasswordHelper passwordHelper;
	
	@Autowired
	LoginUser loginUser;

	private boolean useNewProductInfo = false;

	private final List<String> beautyLargeTextAttributes = new ArrayList<>(
			Arrays.asList("beauty_product_details", "beauty_ingredients", "beauty_instructions_for_use"));

	final private List<String> allLargeTextAttributes = new ArrayList<>(
			Arrays.asList(new String[] { "sustainability", "product_details", "care_instruction" }));

	private final List<String> googleMapsCountryCodes = new ArrayList<>(Arrays.asList("SA", "AE", "KW", "BH", "QA", "OM"));

	@PostConstruct
	public void init() {
		ServiceConfigs.addConfigListener(this);
		onConfigsUpdated(ServiceConfigs.getConsulServiceMap());
	}

	@PreDestroy
	public void onDestroy() {
		ServiceConfigs.removeConfigListener(this);
	}

	@Override
	public void onConfigsUpdated(Map<String, Object> newConfigs) {
		try {
			if (MapUtils.isNotEmpty(newConfigs)) {

				/**
				 * useNewProductInfo
				 */
				if (ObjectUtils.isNotEmpty(newConfigs.get("useNewProductInfo"))) {
					useNewProductInfo = Boolean
							.parseBoolean(newConfigs.get("useNewProductInfo").toString().toLowerCase());
				} else {
					useNewProductInfo = false;
				}

				/**
				 * beautyLargeTextAttributes
				 */
				if (ObjectUtils.isNotEmpty(newConfigs.get(Constants.BEAUTYLRGTXT_ATTR))) {
					beautyLargeTextAttributes.clear();
					final List newList = (newConfigs.get(Constants.BEAUTYLRGTXT_ATTR) instanceof List)
							? (List) newConfigs.get(Constants.BEAUTYLRGTXT_ATTR)
							: ((newConfigs.get(Constants.BEAUTYLRGTXT_ATTR).getClass().getCanonicalName()
									.endsWith("[]"))
											? new ArrayList(Arrays.asList(newConfigs.get(Constants.BEAUTYLRGTXT_ATTR)))
											: new ArrayList());
					for (final Object e : newList) {
						if (e != null && StringUtils.isNotEmpty(e.toString())) {
							beautyLargeTextAttributes.add(e.toString());
						}
					}
				} else {
					beautyLargeTextAttributes.clear();
				}

				/**
				 * allLargeTextAttributes
				 */
				if (ObjectUtils.isNotEmpty(newConfigs.get(Constants.LRGTXT_ATTR))) {
					allLargeTextAttributes.clear();
					final List newList = (newConfigs.get(Constants.LRGTXT_ATTR) instanceof List)
							? (List) newConfigs.get(Constants.LRGTXT_ATTR)
							: ((newConfigs.get(Constants.LRGTXT_ATTR).getClass().getCanonicalName().endsWith("[]"))
									? new ArrayList(Arrays.asList(newConfigs.get(Constants.LRGTXT_ATTR)))
									: new ArrayList());
					for (final Object e : newList) {
						if (e != null && StringUtils.isNotEmpty(e.toString())) {
							allLargeTextAttributes.add(e.toString());
						}
					}
				} else {
					allLargeTextAttributes.clear();
				}

				if (ObjectUtils.isNotEmpty(newConfigs.get(Constants.GOOGLE_MAP_COUNTRY_CODE))) {
					googleMapsCountryCodes.clear();
					final List newList = (newConfigs.get(Constants.GOOGLE_MAP_COUNTRY_CODE) instanceof List)
							? (List) newConfigs.get(Constants.GOOGLE_MAP_COUNTRY_CODE)
							: ((newConfigs.get(Constants.GOOGLE_MAP_COUNTRY_CODE).getClass().getCanonicalName()
									.endsWith("[]"))
											? new ArrayList(
													Arrays.asList(newConfigs.get(Constants.GOOGLE_MAP_COUNTRY_CODE)))
											: new ArrayList());
					for (final Object e : newList) {
						if (e != null && StringUtils.isNotEmpty(e.toString())) {
							googleMapsCountryCodes.add(e.toString());
						}
					}
				} else {
					googleMapsCountryCodes.clear();
				}

			} else {
				useNewProductInfo = false;
			}

		} catch (Exception e) {
			LOGGER.error("consul onConfigsUpdated error:  {} ", e.getMessage());
			useNewProductInfo = false;
		}
	}

	@Override
	public CustomerWishlistResponse getWishList(CustomerWishlistV5Request request) {

		return getWishlist.get(request, client, vmUrl, restTemplate);
	}

	@Override
	public CustomerOmsResponsedto customerOmslist(@Valid CustomerOmslistrequest request) {
		CustomerOmsResponsedto customerOmsResponsedto = new CustomerOmsResponsedto();
		if (request.getOffset() < 0) {
			request.setOffset(0);
		}
		if (request.getPageSize() < 0) {
			request.setPageSize(20);
		}
		Page<CustomerEntity> customerLists = null;
		int totalCount = 20;
		customerLists = findOmsCustomerlist(request, totalCount);
		createResponseObj(customerOmsResponsedto, customerLists, totalCount, request.getPageSize());
		return customerOmsResponsedto;
	}

	@Override
	public CustomerOMSDeleteLoginHistoryResponse customerOmsDeleteLogin(@Valid CustomerOMSDeleteLoginHistoryRequest request){
		CustomerOMSDeleteLoginHistoryResponse customerOMSDeleteLoginHistoryResponse = new CustomerOMSDeleteLoginHistoryResponse();
		try {
			if(request.getCustomerId() != null) {
				CustomerEntity customerEntity = customerEntityRepository.findByEntityId(request.getCustomerId());
				LOGGER.info("customerEntity " + customerEntity);
				if (ObjectUtils.isNotEmpty(customerEntity) && request.getDeviceIds().length > 0) {
						Set<String> deviceIdsSet = new HashSet<>(Arrays.asList(request.getDeviceIds()));
						Set<LoginHistory> remainingLoginDetails = customerEntity.getLoginHistories().stream()
								.filter(his -> his != null &&
										StringUtils.isNotEmpty(his.getDeviceId()) &&
										!deviceIdsSet.contains(his.getDeviceId()))
								.collect(Collectors.toSet());
						customerEntity.setLoginHistories(remainingLoginDetails);
						customerEntityRepository.save(customerEntity);
						customerOMSDeleteLoginHistoryResponse.setStatus(true);
						customerOMSDeleteLoginHistoryResponse.setStatusCode("200");
						customerOMSDeleteLoginHistoryResponse.setStatusMsg("Success");
						return customerOMSDeleteLoginHistoryResponse;
				} else {
					customerOMSDeleteLoginHistoryResponse.setStatus(false);
					customerOMSDeleteLoginHistoryResponse.setStatusCode("400");
					customerOMSDeleteLoginHistoryResponse.setStatusMsg("No Such Customer");
					return customerOMSDeleteLoginHistoryResponse;
				}
			}else{
				customerOMSDeleteLoginHistoryResponse.setStatus(false);
				customerOMSDeleteLoginHistoryResponse.setStatusCode("400");
				customerOMSDeleteLoginHistoryResponse.setStatusMsg("Customer Id Is Required");
				return customerOMSDeleteLoginHistoryResponse;
			}
		} catch (Exception e) {
			customerOMSDeleteLoginHistoryResponse.setStatus(false);
			customerOMSDeleteLoginHistoryResponse.setStatusCode("400");
			customerOMSDeleteLoginHistoryResponse.setStatusMsg("errorMessage" + " " + e.getMessage());
			return customerOMSDeleteLoginHistoryResponse;
		}

	}

	private void createResponseObj(CustomerOmsResponsedto customerOmsResponsedto, Page<CustomerEntity> customerLists,
			int totalCount, int pageSize) {
		if (null != customerLists && CollectionUtils.isNotEmpty(customerLists.getContent())) {
			List<CustomerOmsResponse> customers = new ArrayList<>();
			int totaPageSize = 0;
			for (CustomerEntity customer : customerLists.getContent()) {
				CustomerOmsResponse customerObj = new CustomerOmsResponse();
				customerObj.setCustomerEmail(customer.getEmail());
				customerObj.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
				customerObj.setCustomerId(customer.getEntityId());
				customerObj.setGender(customer.getGender());
				customerObj.setMobileNumber(customer.getPhoneNumber());
				setCountryName(customer, customerObj);
				customerObj.setGroup("GENERAl");

				customerObj.setIsReferral(customer.getReferralUser() != null ? "true" : "false");

				customerObj.setCustomerSince(customer.getCreatedAt().toString());
				if (null != customer.getWebsiteId())
					customerObj.setWebsite(String.valueOf(customer.getWebsiteId()));
				setCustomerSignUpBy(customer, customerObj);
				setCustomerCurrentSignUpBy(customer, customerObj);
				customerObj.setLastSignedInTimestamp(customer.getLastSignedInTimestamp());
				customers.add(customerObj);
			}

			customerOmsResponsedto.setTotalCount(totalCount);
			if (totalCount != 0) {
				totaPageSize = totalCount / pageSize;
			}
			customerOmsResponsedto.setTotalPageSize(totaPageSize);

			customerOmsResponsedto.setStatus(true);
			customerOmsResponsedto.setStatusCode("200");
			customerOmsResponsedto.setStatusMsg("Customer orders fetched successfully!");
			customerOmsResponsedto.setResponse(customers);
		} else {

			customerOmsResponsedto.setStatus(false);
			customerOmsResponsedto.setStatusCode("201");
			customerOmsResponsedto.setStatusMsg("No Result Found!");
		}
	}

	private void setCustomerCurrentSignUpBy(CustomerEntity customer, CustomerOmsResponse customerObj) {
		if (customer.getSignedInNowUsing() == null || customer.getSignedInNowUsing() == 0) {
			customerObj.setCurrentSignInBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_EMAIL);
		} else if (customer.getSignedInNowUsing() == 1) {
			customerObj.setCurrentSignInBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_GOOGLE);
		} else if (customer.getSignedInNowUsing() == 2) {
			customerObj.setCurrentSignInBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_APPLE);
		}

	}

	private void setCustomerSignUpBy(CustomerEntity customer, CustomerOmsResponse customerObj) {
		if (customer.getSignedUpUsing() == null || customer.getSignedUpUsing() == 0) {
			customerObj.setSignUpBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_EMAIL);
		} else if (customer.getSignedUpUsing() == 1) {
			customerObj.setSignUpBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_GOOGLE);
		} else if (customer.getSignedUpUsing() == 2) {
			customerObj.setSignUpBy(UtilityCustomerConatant.CUSTOMER_LOGIN_USING_APPLE);
		}
	}

	private void setCountryName(CustomerEntity customer, CustomerOmsResponse customerObj) {
		if (null != customer.getWebsiteId() && customer.getWebsiteId() == 1) {
			customerObj.setCountryName("SA");

		} else if (null != customer.getWebsiteId() && customer.getWebsiteId() == 2) {
			customerObj.setCountryName("UAE");

		}
	}

	private Page<CustomerEntity> findOmsCustomerlist(CustomerOmslistrequest request, int totalCount) {
		Page<CustomerEntity> customerLists = null;

		List<String> storeIds = new ArrayList<>();
		List<String> websites = new ArrayList<>();

		if ((request.getFilters().getStoreId() != null)) {
			storeIds = request.getFilters().getStoreId();
		}
		if ((request.getFilters().getWebSite() != null)) {
			websites = request.getFilters().getWebSite();
		}
		Pageable pageable = PageRequest.of(request.getOffset(), request.getPageSize(),
				Sort.by("createdAt").descending());

		final Query query = new Query().with(pageable);
		final List<Criteria> criteria = new ArrayList<>();
		if (StringUtils.isNotBlank(request.getQuery())) {

			String customerName = null != request.getFilters().getCustomerName()
					? request.getFilters().getCustomerName()
					: "";
			String customerEmail = null != request.getFilters().getCustomerEmail()
					? request.getFilters().getCustomerEmail()
					: "";
			String entityId = null != request.getFilters().getCustomerId() ? request.getFilters().getCustomerId() : "";
			String mobileNumber = null != request.getFilters().getMobileNumber()
					? request.getFilters().getMobileNumber()
					: "";

			if (request.getQuery().replace(" ", "").matches("\\+[0-9]+")) {

				mobileNumber = request.getQuery();

			} else if (request.getQuery().matches("[0-9]+")) {

				entityId = request.getQuery();

			} else if (request.getQuery().contains("@")) {

				customerEmail = request.getQuery();
			} else {

				customerName = request.getQuery();
			}

			if (customerName.matches("[a-zA-Z]+") || "".equals(customerName)) {

				if (StringUtils.isNotBlank(customerName)) {

					criteria.add(Criteria.where(FIRST_NAME).regex(customerName));
				}
				if (StringUtils.isNotBlank(customerEmail)) {

					criteria.add(Criteria.where("email").regex(customerEmail));
				}
				if (StringUtils.isNotBlank(mobileNumber)) {

					criteria.add(Criteria.where(PHONE_NUMBER).is(mobileNumber));

				}
				if (StringUtils.isNotBlank(entityId)) {

					criteria.add(Criteria.where("entityId").is(Integer.parseInt(entityId)));
				}

				if (!criteria.isEmpty()) {
					query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[criteria.size()])));
				}
				customerLists = getFilteredList(customerLists, pageable, query);

			} else {
				customerLists = getCustomerList(customerLists, pageable, query, criteria, customerName);
			}

		} else if (StringUtils.isNotBlank(request.getFilters().getMobileNumber())) {

			criteria.add(Criteria.where(PHONE_NUMBER).is(request.getFilters().getMobileNumber()));

			if (!criteria.isEmpty()) {
				query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[criteria.size()])));
			}

			customerLists = getFilteredList(customerLists, pageable, query);

		} else if (null != request.getFilters() && null == request.getFilters().getCustomerEmail()
				&& null == request.getFilters().getCustomerId() && null == request.getFilters().getCustomerName()
				&& null == request.getFilters().getGender() && null == request.getFilters().getMobileNumber()) {

			List<CustomerEntity> customerList = null;

			if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("GCC")) {
				customerList = mongoGccTemplate.find(query, CustomerEntity.class);
				customerLists = PageableExecutionUtils.getPage(customerList, pageable,
						() -> mongoGccTemplate.count(query, CustomerEntity.class));
			} else if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("IN")) {
				customerList = mongoInTemplate.find(query, CustomerEntity.class);
				customerLists = PageableExecutionUtils.getPage(customerList, pageable,
						() -> mongoInTemplate.count(query, CustomerEntity.class));
			}

		} else {

			if (request.getFilters().getCustomerName().matches("[a-zA-Z]+")
					|| "".equals(request.getFilters().getCustomerName())) {

				if (StringUtils.isNotBlank(request.getFilters().getCustomerName())) {

					criteria.add(Criteria.where(FIRST_NAME).regex(request.getFilters().getCustomerName()));
				}
				if (StringUtils.isNotBlank(request.getFilters().getCustomerEmail())) {

					criteria.add(Criteria.where("email").regex(request.getFilters().getCustomerEmail()));
				}
				if (StringUtils.isNotBlank(request.getFilters().getMobileNumber())) {

					criteria.add(Criteria.where(PHONE_NUMBER).is(request.getFilters().getMobileNumber()));

				}
				if (StringUtils.isNotBlank(request.getFilters().getCustomerId())) {

					criteria.add(Criteria.where("entityId").is(Integer.parseInt(request.getFilters().getCustomerId())));

				}
				if (StringUtils.isNotBlank(request.getFilters().getGender())) {

					criteria.add(Criteria.where("gender").is(request.getFilters().getGender()));

				}
				if (StringUtils.isNotBlank(request.getFilters().getSource())) {

					criteria.add(Criteria.where("clientSource").is(request.getFilters().getSource()));
				}
				if (!storeIds.isEmpty()) {

					criteria.add(Criteria.where("storeId").in(storeIds));

				}
				if (!websites.isEmpty()) {

					criteria.add(Criteria.where("websiteId").in(websites));
				}

				if (!criteria.isEmpty()) {
					query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[criteria.size()])));
				}

				customerLists = getFilteredList(customerLists, pageable, query);

			} else {
				customerLists = getCustomerList(customerLists, pageable, query, criteria,
						request.getFilters().getCustomerName());
			}
		}
		return customerLists;
	}

	private Page<CustomerEntity> getFilteredList(Page<CustomerEntity> customerLists, Pageable pageable,
			final Query query) {
		List<CustomerEntity> customerList = null;
		if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("GCC")) {
			customerList = mongoGccTemplate.find(query, CustomerEntity.class, CUSTOMER_ENTITY);
			customerLists = PageableExecutionUtils.getPage(customerList, pageable,
					() -> mongoGccTemplate.count(query, CustomerEntity.class));
		} else if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("IN")) {
			customerList = mongoInTemplate.find(query, CustomerEntity.class, CUSTOMER_ENTITY);
			customerLists = PageableExecutionUtils.getPage(customerList, pageable,
					() -> mongoInTemplate.count(query, CustomerEntity.class));
		}
		return customerLists;
	}

	private Page<CustomerEntity> getCustomerList(Page<CustomerEntity> customerLists, Pageable pageable,
			final Query query, final List<Criteria> criteria, String customerName) {
		List<CustomerEntity> customerList = null;
		criteria.add(Criteria.where(FIRST_NAME).regex(customerName));

		if (!criteria.isEmpty()) {
			query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[criteria.size()])));
		}

		if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("GCC")) {
			customerList = mongoGccTemplate.find(query, CustomerEntity.class, CUSTOMER_ENTITY);
			customerLists = PageableExecutionUtils.getPage(customerList, pageable,
					() -> mongoGccTemplate.count(query, CustomerEntity.class));
		} else if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("IN")) {
			customerList = mongoInTemplate.find(query, CustomerEntity.class, CUSTOMER_ENTITY);
			customerLists = PageableExecutionUtils.getPage(customerList, pageable,
					() -> mongoInTemplate.count(query, CustomerEntity.class));
		}
		return customerLists;
	}

	@Override
	public CustomerUpdateProfileResponse customerDetails(@Valid CustomerDetailsRequest request,
			@RequestHeader Map<String, String> httpRequestHeadrs) {

		return getCustomer.get(request.getCustomerId(), client, "0", request.getCustomerEmail(),
				request.getCustomerPhoneNo());

	}

	@Override
	public ProductStatusResponse getProductQty(Map<String, String> requestHeader,
			ProductStatusRequest productStatusReq) {
		ProductStatusResponse resp = new ProductStatusResponse();
		ProductStatusResBody respBody = new ProductStatusResBody();
		try {
			Integer storeId = productStatusReq.getStoreId();
			List<String> skuList = new ArrayList<>();
			for (ProductInfo info : productStatusReq.getProducts()) {

				skuList.add(info.getSku());
			}
			productStatusReq = new ProductStatusRequest();
			productStatusReq.setSkus(skuList);
			productStatusReq.setStoreId(storeId);

			List<ProductValue> productStatus = getWishlist.getInventoryQty(productStatusReq);

			for (ProductValue value : productStatus) {

				value.setProcuctId(value.getSku());
			}
			respBody.setProductStatus(productStatus);

			resp.setResponse(respBody);

			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("SUCCESS!!");

			resp.setStatusMsg("SUCCESS!!");
		} catch (Exception e) {

			ErrorType errorType = new ErrorType();

			errorType.setErrorCode("204");
			errorType.setErrorMessage(e.getMessage());

			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("ERROR!!");

			resp.setError(errorType);
		}
		return resp;
	}

	@Override
	public ProductDetailsResponseV4 getProductInfo(@Valid GetProductV4Request request, String xHeaderToken) {
		if (useNewProductInfo) {
			return getNewProductInfo(request, xHeaderToken);
		}
		ProductDetailsResponseV4 resp = new ProductDetailsResponseV4();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.X_HEADER_TOKEN, xHeaderToken);

		GetProductDescRequest mulinRequest = new GetProductDescRequest();
		mulinRequest.setProduct_id(request.getProductId());
		mulinRequest.setStore_id(request.getStoreId().toString());
		mulinRequest.setSku(request.getSku());
		HttpEntity<GetProductDescRequest> requestBody = new HttpEntity<>(mulinRequest, requestHeaders);

		String listingMulinUrl = mulinUrl + "/v1/products/getBeautyAttrs";

		ResponseEntity<MulinProductDescRes> response;
		try {

			response = restTemplate.exchange(listingMulinUrl, HttpMethod.POST, requestBody, MulinProductDescRes.class);
			MulinProductDescRes body = response.getBody();
			LOGGER.info("Listing VM Response: {}", mapper.writeValueAsString(body));

			if (response.getStatusCode() == HttpStatus.OK && null != body && 200 == body.getStatusCode()) {

				ProductDetailsResponseV4DTO productResponse = new ProductDetailsResponseV4DTO();

				Map<String, ProductInfoValue> productInfoRes = new LinkedHashMap<>();

				List<MulinProductDetails> beautyAttrs = body.getBeautyAttrs();

				for (MulinProductDetails attribute : beautyAttrs) {

					ProductInfoValue productDes = new ProductInfoValue();
					productDes.setName(attribute.getName());
					productDes.setBeautyCode(attribute.getBeautyCode());
					productInfoRes.put(attribute.getLabel(), productDes);

				}

				productResponse.setProductInfos(productInfoRes);
				resp.setResponse(productResponse);

				resp.setStatusCode("200");
				resp.setStatusMsg(Constants.SUCCESS_MSG);
				resp.setStatus(true);

			} else {
				resp.setStatusCode("204");
				resp.setStatus(false);
				resp.setStatusMsg("product desc response not found!:");
				return resp;
			}

		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error(e.getMessage());
			resp.setStatusCode("203");
			resp.setStatus(false);
			resp.setStatusMsg("Exception in rest mulin for description!");
			return resp;

		}
		return resp;
	}

	private ProductDetailsResponseV4 getNewProductInfo(GetProductV4Request request, String xHeaderToken) {
		ProductDetailsResponseV4 resp = new ProductDetailsResponseV4();
		try {
			ProductListRequestV2 elasticRequest = new ProductListRequestV2();
			HashMap<String, List<String>> filters = new HashMap<>();
			filters.put("objectID", Arrays.asList(new String[] { request.productId.toString() }));
			elasticRequest.setFilters(filters);
			elasticRequest.setNumericFilters(new ArrayList<>());
			elasticRequest.setPageOffset(0);
			elasticRequest.setPageSize(20);
			elasticRequest.setStoreId(request.getStoreId());
			elasticRequest.setCategoryLevel(3);

			if (env.equals("live")) {
				elasticRequest.setEnv(Constants.LISTING_VM_ENV_LIVE);
			} else {
				elasticRequest.setEnv(env);
			}

			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add(Constants.USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
			requestHeaders.add(Constants.X_HEADER_TOKEN, xHeaderToken);
			HttpEntity<ResponseDetailElastic> requestBody = new HttpEntity(elasticRequest, requestHeaders);
			String detailsVmUrl = vmUrl + "/api/detail";

			LOGGER.info("Details VM url: {}", detailsVmUrl);

			ResponseEntity<ResponseDetailElastic> response = restTemplate.exchange(detailsVmUrl, HttpMethod.POST,
					requestBody, ResponseDetailElastic.class);
			ResponseDetailElastic body = response.getBody();
			if (response.getStatusCode() == HttpStatus.OK && body != null && CollectionUtils.isNotEmpty(body.getHits())
					&& body.getHits().get(0) != null) {
				final ResponseDetailElastic elasticResponse = body;
				final HitsDetailResponseElastic elasticProduct = elasticResponse.getHits().get(0);

				final boolean isBeautyProduct = (CollectionUtils.isNotEmpty(elasticProduct.getCategoryIds())
						&& elasticProduct.getCategoryIds().stream()
								.filter(cId -> cId != null && Integer.valueOf(11765).equals(cId)).findFirst()
								.orElse(null) != null);

				ProductDetailsResponseV4DTO productResponse = new ProductDetailsResponseV4DTO();

				Map<String, ProductInfoValue> productInfoRes = new LinkedHashMap<>();
				final Map<String, ProductAttributeElastic> productAttributeFilters = MapUtils.isNotEmpty(
						elasticProduct.getProductAttributeFilters()) ? elasticProduct.getProductAttributeFilters()
								: new LinkedHashMap<>();
				if (isBeautyProduct) {
					for (final String attributeCode : beautyLargeTextAttributes) {
						addProductInfo(attributeCode, productAttributeFilters, productInfoRes);
					}
				} else {
					for (final String attributeCode : allLargeTextAttributes) {
						addProductInfo(attributeCode, productAttributeFilters, productInfoRes);
					}
				}
				productResponse.setProductInfos(productInfoRes);
				resp.setResponse(productResponse);
				resp.setStatusCode("200");
				resp.setStatusMsg("SUCCESS");
				resp.setStatus(true);
			} else {
				resp.setStatusCode("204");
				resp.setStatus(false);
				resp.setStatusMsg("product desc response not found!:");
				return resp;
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
			resp.setStatusCode("203");
			resp.setStatus(false);
			resp.setStatusMsg("Exception in new Product Info call!");
		}
		return resp;
	}

	private void addProductInfo(String code, Map<String, ProductAttributeElastic> productAttributeFilters,
			Map<String, ProductInfoValue> productInfo) {
		if (StringUtils.isNotEmpty(code) && productAttributeFilters != null && productInfo != null
				&& productAttributeFilters.get(code) != null) {
			final ProductAttributeElastic pae = productAttributeFilters.get(code);
			if (StringUtils.isNotEmpty(pae.getLabel()) && StringUtils.isNotEmpty(pae.getName())) {
				ProductInfoValue productDes = new ProductInfoValue();
				productDes.setName(pae.getName());
				productDes.setBeautyCode(code);
				productInfo.put(pae.getLabel(), productDes);
			}
		}
	}

	public GetLocationGoogleMapsResponse getLocationGoogleMaps(@Valid GetLocationGoogleMapsRequest request) {

		GetLocationGoogleMapsResponse response = new GetLocationGoogleMapsResponse();
		GetLocationGoogleMaps resp = new GetLocationGoogleMaps();
		String reverseGeocodingUrl = "https://maps.googleapis.com/maps/api/geocode/json";
		String langCode = "en";
		Stores store = null;

		if (null != request.getStoreId()) {
			List<Stores> stores = staticComponents.getStoresArray();
			store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId())).findAny()
					.orElse(null);

			langCode = CommonUtility.getLanguageCode(store);
		}
		String language = langCode.equalsIgnoreCase("ar") ? Constants.LANGUAGE_AR : Constants.LANGUAGE_EN;

		try {
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add(Constants.USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

			HttpEntity<String> requestBody = new HttpEntity<>(requestHeaders);
			Map<String, Object> parameters = new HashMap<>();

			if (null != request.getPlaceId() && StringUtils.isNotEmpty(request.getPlaceId())) {

				reverseGeocodingUrl = reverseGeocodingUrl + PLACE_ID + request.getPlaceId() + KEY + googleMapsApiKey
						+ language;

			} else if (null != request.getLatitude() && null != request.getLongitude()) {

				resp.setLatitude(request.getLatitude());
				resp.setLongitude(request.getLongitude());
				reverseGeocodingUrl = reverseGeocodingUrl + LATLNG + request.getLatitude() + ","
						+ request.getLongitude() + KEY + googleMapsApiKey + language;
			}

			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(reverseGeocodingUrl);

			ResponseEntity<GoogleMapsGeocodingResponse> responseGoogle = restTemplate.exchange(
					builder.buildAndExpand(parameters).toUri(), HttpMethod.GET, requestBody,
					GoogleMapsGeocodingResponse.class);

			if (responseGoogle.getStatusCode() == HttpStatus.OK) {

				GoogleMapsGeocodingResponse geoCodingResponse = responseGoogle.getBody();
				List<GoogleResults> googleResults = geoCodingResponse.getResults();

				if (null != googleResults) {

					boolean isLocalityPresent = googleResults.stream().filter(o -> o.getTypes().get(0).equals(LOCALITY))
							.findFirst().isPresent();

					for (GoogleResults result : googleResults) {

						if (null != result.getTypes() && !result.getTypes().isEmpty()) {
							String resultType = result.getTypes().get(0);

							if (null != resultType && resultType.equalsIgnoreCase(LOCALITY)) {

								if (ObjectUtils.isEmpty(resp.getFormattedAddress())) {
									resp.setFormattedAddress(result.getFormattedAddress());
								}
								setCityCountryProvince(resp, result);

								if (null != result.getGeometry() && null != result.getGeometry().getLocation()
										&& null == resp.getLatitude()) {
									resp.setLatitude(result.getGeometry().getLocation().getLatitude());
								}
								if (null != result.getGeometry() && null != result.getGeometry().getLocation()
										&& null == resp.getLongitude()) {

									resp.setLongitude(result.getGeometry().getLocation().getLongitude());
								}
							} else if (ObjectUtils.isEmpty(resp.getCity())) {

								if (ObjectUtils.isEmpty(resp.getFormattedAddress())) {
									resp.setFormattedAddress(result.getFormattedAddress());
								}
								if (!isLocalityPresent) {
									setCityCountryProvince(resp, result);
								}
								if (null != result.getGeometry() && null != result.getGeometry().getLocation()
										&& null == resp.getLatitude()) {
									resp.setLatitude(result.getGeometry().getLocation().getLatitude());
								}
								if (null != result.getGeometry() && null != result.getGeometry().getLocation()
										&& null == resp.getLongitude()) {
									resp.setLongitude(result.getGeometry().getLocation().getLongitude());
								}

								if (StringUtils.isEmpty(resp.getArea())) {
									setAreaFromOthers(resp, result);
								}

							}
						}

					}

					if (ObjectUtils.isEmpty(resp.getArea())) {

						for (GoogleResults result : googleResults) {

							if (null != result.getTypes() && result.getTypes().size() > 0
									&& ObjectUtils.isEmpty(resp.getArea())) {
								String resultType = result.getTypes().get(0);

								if (null != resultType && resultType.equalsIgnoreCase("political")) {

									List<GoogleAddressComponent> addressComponents = result.getAddressComponents();

									if (null != addressComponents && addressComponents.size() > 0) {
										for (GoogleAddressComponent addressComponent : addressComponents) {

											if (null != addressComponent.getTypes()
													&& addressComponent.getTypes().size() > 1) {

												String political = addressComponent.getTypes().get(0);

												if (null != addressComponent.getTypes().get(0)
														&& addressComponent.getTypes().get(0)
																.equalsIgnoreCase("political")
														&& null != addressComponent.getTypes().get(1)
														&& addressComponent.getTypes().get(1)
																.equalsIgnoreCase("sublocality")) {

													resp.setArea(addressComponent.getLongName());

												}
											}
										}
									}
								} else if (null != resultType && resultType.equalsIgnoreCase("neighborhood")) {

									List<GoogleAddressComponent> addressComponents = result.getAddressComponents();

									if (null != addressComponents && !addressComponents.isEmpty()) {
										for (GoogleAddressComponent addressComponent : addressComponents) {

											if (null != addressComponent.getTypes()
													&& !addressComponent.getTypes().isEmpty()) {

												String neighborhood = addressComponent.getTypes().get(0);

												if (null != neighborhood
														&& neighborhood.equalsIgnoreCase("neighborhood")) {

													resp.setArea(addressComponent.getLongName());

												}
											}
										}
									}
								}
							}

						}
					}
					/** if resp lat & long is empty then set the requirested values **/
					if (null != request.getLatitude() && null != request.getLongitude() && null == resp.getLatitude()
							&& null == resp.getLongitude()) {

						resp.setLatitude(request.getLatitude());
						resp.setLongitude(request.getLongitude());
					}
				}
				int adrsmapperCityFlag = (int) TtlMode.CITY_SEARCH.getValue();
				CityBucketObject cityBucketObject = null;
				String key = null;
				long now = Instant.now().toEpochMilli();
				if (adrsmapperCityFlag > 0 && StringUtils.isNotBlank(resp.getCity()) && null != request.getStoreId()) {
					key = resp.getCity().concat("#").concat(request.getStoreId().toString());
					LOGGER.info("cityBucketObject from cache key:" + key);
					cityBucketObject = (CityBucketObject) redisHelper.get(CITY_CACHE_NAME, key, CityBucketObject.class);
					LOGGER.info("cityBucketObject:" + mapper.writeValueAsString(cityBucketObject));
				} else if (adrsmapperCityFlag == 0 && null != resp.getCity()) {
					key = resp.getCity().concat("#").concat(request.getStoreId().toString());
					redisHelper.remove(CITY_CACHE_NAME, key);
				}

				if (StringUtils.isNotEmpty(resp.getCity()) && ObjectUtils.isEmpty(cityBucketObject)
						&& null != resp.getCountryShort() && null != store
								&& googleMapsCountryCodes.contains(resp.getCountryShort())){
					checkCityServiceable(resp, langCode, request.getStoreId());

					if (ObjectUtils.isEmpty(resp.getArea())) {
						resp.setArea(resp.getCity());
					}
						cityBucketObject = new CityBucketObject();
						cityBucketObject.setOriginAt(now);
						cityBucketObject.setCreateCount(0);
					
					cityBucketObject.setResponse(resp);
					cityBucketObject.setCreatedAt(now);
					cityBucketObject.setExpiresAt((now + getExpiryPeriodMilli()));
					if (adrsmapperCityFlag > 0 && StringUtils.isNotEmpty(resp.getCity())
							&& null != request.getStoreId() && null != resp.getSuccessResponse() &&  resp.getSuccessResponse()) {
						key = resp.getCity().concat("#").concat(request.getStoreId().toString());
						LOGGER.info("cityBucketObject to cache set key:" + key);
						redisHelper.put(CITY_CACHE_NAME, key, cityBucketObject, TtlMode.CITY_SEARCH);
						LOGGER.info("data fetched from adrs mapper :" + mapper.writeValueAsString(cityBucketObject));
					}
						
				} else if (ObjectUtils.isNotEmpty(cityBucketObject) && null != cityBucketObject.getResponse()
						&& googleMapsCountryCodes.contains(resp.getCountryShort())) {
					GetLocationGoogleMaps cachedResp = cityBucketObject.getResponse();
					if(null != cachedResp) {
						resp.setServiceable(cachedResp.getServiceable());
						resp.setCity(cachedResp.getCity());
						resp.setRegion(cachedResp.getRegion());
						resp.setRegionId(cachedResp.getRegionId());
					}
					LOGGER.info("data fetched from cache"+mapper.writeValueAsString(resp));
				} else {

					if (null != resp.getCountryShort() && null != store
							&& googleMapsCountryCodes.contains(resp.getCountryShort())) {
						resp.setCity(null);
						resp.setServiceable(true);
					}
				}

				response.setResponse(resp);
				response.setStatusCode("200");
				response.setStatusMsg(Constants.SUCCESS_MSG);
				response.setStatus(true);

			} else {
				response.setStatusCode("204");
				response.setStatus(false);
				response.setStatusMsg("reverse geocoding response not found!:");
				return response;
			}
		} catch (Exception e) {
			LOGGER.error("Error in maps" + e.getMessage());
		}
		return response;
	}

	private void setCityCountryProvince(GetLocationGoogleMaps resp, GoogleResults result) {
		List<GoogleAddressComponent> addressComponents = result.getAddressComponents();

		if (null != addressComponents && !addressComponents.isEmpty()) {
			for (GoogleAddressComponent addressComponent : addressComponents) {

				if (null != addressComponent.getTypes() && !addressComponent.getTypes().isEmpty()) {

					String locality = addressComponent.getTypes().get(0);
					String sub = "";
					if (addressComponent.getTypes().size() > 1) {
						sub = addressComponent.getTypes().get(1);
					}

					if (null != locality && locality.equalsIgnoreCase(LOCALITY)) {

						resp.setCity(addressComponent.getLongName());

					} else if (null != locality && locality.equalsIgnoreCase("country")) {

						resp.setCountry(addressComponent.getLongName());
						resp.setCountryShort(addressComponent.getShortName());

					} else if (null != locality && locality.equalsIgnoreCase("administrative_area_level_1")) {

						resp.setProvince(addressComponent.getLongName());

					} else if (null != locality && locality.equalsIgnoreCase("political") && null != sub
							&& sub.equalsIgnoreCase("sublocality")) {

						resp.setArea(addressComponent.getLongName());

					} else if (null != locality && locality.equalsIgnoreCase("administrative_area_level_2")
							&& ObjectUtils.isEmpty(resp.getArea())) {

						resp.setArea(addressComponent.getLongName());

					} else if (null != locality && locality.equalsIgnoreCase("colloquial_area")
							&& ObjectUtils.isEmpty(resp.getArea())) {

						resp.setArea(addressComponent.getLongName());

					}
				}
			}
		}
	}

	public PlacesAutocompleteGoogleMapsResponse getGooglePlacesForAutocompleteText(
			@Valid PlacesAutocompleteGoogleMapsRequest request) {

		PlacesAutocompleteGoogleMapsResponse response = new PlacesAutocompleteGoogleMapsResponse();
		response.setStatusCode("500");
		response.setStatus(false);
		response.setStatusMsg("Error in autocomplete places");

		List<GoogleMapsPlaceDetails> resultList = null;

		String placesUrl = "https://maps.googleapis.com/maps/api/place/autocomplete/json";
		HttpURLConnection conn = null;
		StringBuilder jsonResults = new StringBuilder();
		try {
			StringBuilder sb = new StringBuilder(placesUrl);
			sb.append("?sensor=false");
			sb.append(KEY + googleMapsApiKey);
			sb.append("&input=" + URLEncoder.encode(request.getPlaceText(), "utf8"));

			List<Stores> stores = staticComponents.getStoresArray();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
					.findAny().orElse(null);

			String langCode = CommonUtility.getLanguageCode(store);
			String language = langCode.equalsIgnoreCase("ar") ? Constants.LANGUAGE_AR : "";
			sb.append(language);

			URL url = new URL(sb.toString());
			conn = (HttpURLConnection) url.openConnection();
			InputStreamReader in = new InputStreamReader(conn.getInputStream());

			int read;
			char[] buff = new char[1024];
			while ((read = in.read(buff)) != -1) {
				jsonResults.append(buff, 0, read);
			}
		} catch (MalformedURLException e) {
			LOGGER.error("Error processing Places API URL", e);
			return response;

		} catch (IOException e) {
			LOGGER.error("Error connecting to Places API", e);
			return response;

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}

		try {
			// Create a JSON object hierarchy from the results
			JSONObject jsonObj = new JSONObject(jsonResults.toString());
			JSONArray predsJsonArray = jsonObj.getJSONArray("predictions");

			// Extract the Place descriptions from the results
			resultList = new ArrayList<>(predsJsonArray.length());
			for (int i = 0; i < predsJsonArray.length(); i++) {
				GoogleMapsPlaceDetails place = new GoogleMapsPlaceDetails();
				place.setPlaceName(predsJsonArray.getJSONObject(i).getString("description"));
				place.setPlaceId(predsJsonArray.getJSONObject(i).getString("place_id"));
				resultList.add(place);
			}
		} catch (JSONException e) {
			LOGGER.error("Error processing JSON results", e);
		}

		response.setPlacesList(resultList);
		response.setStatusCode("200");
		response.setStatusMsg(Constants.SUCCESS_MSG);
		response.setStatus(true);

		return response;

	}

	private void checkCityServiceable(GetLocationGoogleMaps resp, String langCode, Integer storeId) {

		try {
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add(Constants.USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

			GetGoogleCityNameRequest req = new GetGoogleCityNameRequest();
			req.setGoogleCityName(resp.getCity());
			req.setStoreId(storeId);

			HttpEntity<ResponseDetailElastic> requestBody = new HttpEntity(req, requestHeaders);

			String url = adrsmprBaseUrl + "/api/address/google/city";
			ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.POST, requestBody, Map.class);

			if (responseEntity.getStatusCode() == HttpStatus.OK) {
				Map resultBody = responseEntity.getBody();
				LOGGER.info("Google city validate Address Mapper response fetched successfully!!");
				Map result = new HashMap<>();
				if (resultBody != null) {
					result = (Map) resultBody.get("response");

				}
				LOGGER.info("Address Mapper response :: {}", result);

				if (null != result.get("serviceable"))
					resp.setServiceable((Boolean) result.get("serviceable"));

				if (langCode.equalsIgnoreCase("en") && null != result.get("cityEn"))
					resp.setCity((String) result.get("cityEn"));

				if (langCode.equalsIgnoreCase("ar") && null != result.get("cityAr"))
					resp.setCity((String) result.get("cityAr"));

				if (null != result.get("region"))
					resp.setRegion((String) result.get("region"));

				if (null != result.get("regionId")) {
					String regionId = (String) result.get("regionId");
					resp.setRegionId(Integer.parseInt(regionId));
				}
				resp.setSuccessResponse(true);
			}
		} catch (Exception e) {
			LOGGER.error("Error getting address mapper for city " + resp + " ,exception : " + e.getMessage());
		}
	}

	public PlacesAutocompleteGoogleMapsResponse getGooglePlacesForAutocompleteText(String placeText, Integer storeId) {

		PlacesAutocompleteGoogleMapsResponse response = new PlacesAutocompleteGoogleMapsResponse();
		response.setStatusCode("500");
		response.setStatus(false);
		response.setStatusMsg("Error in autocomplete places");

		List<GoogleMapsPlaceDetails> resultList = null;

		String placesUrl = "https://maps.googleapis.com/maps/api/place/autocomplete/json";
		HttpURLConnection conn = null;
		StringBuilder jsonResults = new StringBuilder();
		try {
			StringBuilder sb = new StringBuilder(placesUrl);
			sb.append("?sensor=false");
			sb.append(KEY + googleMapsApiKey);
			sb.append("&input=" + URLEncoder.encode(placeText, "utf8"));

			List<Stores> stores = staticComponents.getStoresArray();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
					.orElse(null);

			String langCode = CommonUtility.getLanguageCode(store);
			String language = langCode.equalsIgnoreCase("ar") ? Constants.LANGUAGE_AR : "";
			sb.append(language);

			URL url = new URL(sb.toString());
			conn = (HttpURLConnection) url.openConnection();
			InputStreamReader in = new InputStreamReader(conn.getInputStream());

			int read;
			char[] buff = new char[1024];
			while ((read = in.read(buff)) != -1) {
				jsonResults.append(buff, 0, read);
			}
		} catch (MalformedURLException e) {
			LOGGER.error("Error processing Places API URL", e);
			return response;

		} catch (IOException e) {
			LOGGER.error("Error connecting to Places API", e);
			return response;

		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}

		try {
			// Create a JSON object hierarchy from the results
			JSONObject jsonObj = new JSONObject(jsonResults.toString());
			JSONArray predsJsonArray = jsonObj.getJSONArray("predictions");

			// Extract the Place descriptions from the results
			resultList = new ArrayList<>(predsJsonArray.length());
			for (int i = 0; i < predsJsonArray.length(); i++) {
				GoogleMapsPlaceDetails place = new GoogleMapsPlaceDetails();
				place.setPlaceName(predsJsonArray.getJSONObject(i).getString("description"));
				place.setPlaceId(predsJsonArray.getJSONObject(i).getString("place_id"));
				resultList.add(place);
			}
		} catch (JSONException e) {
			LOGGER.error("Error processing JSON results", e);
		}

		response.setPlacesList(resultList);
		response.setStatusCode("200");
		response.setStatusMsg(Constants.SUCCESS_MSG);
		response.setStatus(true);

		return response;

	}

	private void setAreaFromOthers(GetLocationGoogleMaps resp, GoogleResults result) {
		List<GoogleAddressComponent> addressComponents = result.getAddressComponents();
		if (null != addressComponents && !addressComponents.isEmpty()) {
			for (GoogleAddressComponent addressComponent : addressComponents) {
				if (null != addressComponent.getTypes() && !addressComponent.getTypes().isEmpty()) {
					String locality = addressComponent.getTypes().get(0);
					String sub = "";
					if (addressComponent.getTypes().size() > 1) {
						sub = addressComponent.getTypes().get(1);
					}
					if (null != locality && locality.equalsIgnoreCase("political") && null != sub
							&& sub.equalsIgnoreCase("sublocality")) {
						resp.setArea(addressComponent.getLongName());
					}
					if (null != locality && locality.equalsIgnoreCase("administrative_area_level_2")
							&& ObjectUtils.isEmpty(resp.getArea())) {
						resp.setArea(addressComponent.getLongName());
					}
				}
			}
		}
	}

	private long getExpiryPeriodMilli() {
		return TtlMode.CITY_SEARCH.getTimeUnit().toMillis(TtlMode.CITY_SEARCH.getValue());
	}

	@Override
	public ResponseEntity<?> refreshAccessToken(AccessTokenRequest tokenRequest,Map<String, String> requestHeader) {
		AccessTokenResponse accessTokenResponse= new AccessTokenResponse();
		try {
			ObjectMapper mapper = new ObjectMapper();

			// Register the JavaTimeModule to handle LocalDate
			mapper.registerModule(new JavaTimeModule());
			mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
			String deviceId = tokenRequest.getDeviceId();
			String refreshToken = tokenRequest.getRefreshToken();
			boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
			CustomerEntity customerEntity = new CustomerEntity();
			String customerEmail = requestHeader.get("x-header-token");
			if(StringUtils.isEmpty(customerEmail) || StringUtils.isBlank(customerEmail)){
				customerEmail= requestHeader.get("X-Header-Token");
			}

			if(StringUtils.isNotEmpty(refreshToken) && StringUtils.isNotBlank(refreshToken)){
				customerEntity = customerEntityRepository.findByLoginHistoriesRefreshToken(refreshToken);
			}

			if((customerEntity == null || ObjectUtils.isEmpty(customerEntity))){
				customerEntity = customerEntityRepository.findByEmail(customerEmail);
			}

			LOGGER.info("customer data found "+ mapper.writeValueAsString(customerEntity));

			if(ObjectUtils.isNotEmpty(customerEntity)){
				boolean refreshTokenMatchingFlag= false;
				String userId = customerEntity.getEmail();
				String accessToken=passwordHelper.generateToken(userId, UUID.randomUUID().toString(),
						customerEntity.getEntityId(), false);;
				if(refreshTokenFlag){
					if((StringUtils.isNotBlank(requestHeader.get(Constants.deviceId)) || StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))){
						userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
						refreshTokenMatchingFlag = true;
					}
				}
				if(refreshTokenMatchingFlag) {
					Set<LoginHistory> loginHistories = customerEntity.getLoginHistories();
					LOGGER.info("customer login histories "+ mapper.writeValueAsString(loginHistories));
					Optional<LoginHistory> recordFilter = customerEntity.getLoginHistories().stream()
							.filter(c -> Objects.nonNull(c.getDeviceId()) && c.getDeviceId().equals(deviceId)).findFirst();

					if (recordFilter.isEmpty() || !recordFilter.get().getRefreshToken().equals(refreshToken)) {
						accessTokenResponse.setStatus(false);
						accessTokenResponse.setMessage("Provided token not valid.");
						accessTokenResponse.setStatusCode("401");
						return ResponseEntity.status(401).body(accessTokenResponse);
					}

					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());
					if (recordFilter.get().getExpiryDate() != null && currentDate.isAfter(recordFilter.get().getExpiryDate())) {
						Set<LoginHistory> remainingLoginDetails = customerEntity.getLoginHistories().stream()
								.filter(his -> {
									boolean isObjectNotEmpty = !ObjectUtils.isEmpty(his);
									boolean isDeviceIdNotEmpty = !StringUtils.isEmpty(his.getDeviceId());
									boolean isDeviceIdNotMatching = !his.getDeviceId().equals(deviceId);
									return isObjectNotEmpty && isDeviceIdNotEmpty && isDeviceIdNotMatching;
								})
								.collect(Collectors.toSet());
						customerEntity.setLoginHistories(remainingLoginDetails);
						client.saveAndFlushCustomerEntity(customerEntity);
						accessTokenResponse.setStatus(false);
						accessTokenResponse.setMessage("Provided token expired");
						accessTokenResponse.setStatusCode("401");
						return ResponseEntity.status(401).body(accessTokenResponse);
					}
					accessToken = passwordHelper.generateToken(userId, UUID.randomUUID().toString(),
							customerEntity.getEntityId(), true);

					LoginHistory loginHistory1 = recordFilter.get();
					if (ObjectUtils.isNotEmpty(loginHistory1)) {
						if (loginHistory1.getExpiryDate() != null) {
							LocalDate loginExpiryDate = loginHistory1.getExpiryDate();
							if (expiryDate.isAfter(loginExpiryDate)) {
								loginHistory1.setExpiryDate(expiryDate);
								customerEntity.setLoginHistories(loginHistories);
								client.saveAndFlushCustomerEntity(customerEntity);
							}
						} else {
							loginHistory1.setExpiryDate(expiryDate);
							loginHistories.add(loginHistory1);
							customerEntity.setLoginHistories(loginHistories);
							client.saveAndFlushCustomerEntity(customerEntity);
						}
					}

				}

				CustomerV4Response response = new CustomerV4Response();
				response.setAccessToken(accessToken);
				Customer customerDto = loginUser.getSavedCustomerInfo(customerEntity, null);
				response.setCustomer(customerDto);
				accessTokenResponse.setStatus(true);
				accessTokenResponse.setMessage("success");
				accessTokenResponse.setStatusCode("200");
				accessTokenResponse.setResponse(response);
				return ResponseEntity.status(200).body(accessTokenResponse);

			}else{
				accessTokenResponse.setStatus(false);
				accessTokenResponse.setMessage("Provided details not valid to update access token.");
				accessTokenResponse.setStatusCode("401");
				return ResponseEntity.status(401).body(accessTokenResponse);
			}

		} catch (Exception e) {
			LOGGER.error("Error in updating access token. " + tokenRequest, e);
			accessTokenResponse.setStatus(false);
			accessTokenResponse.setMessage("Error in updating access token.");
			accessTokenResponse.setStatusCode("401");
			return ResponseEntity.status(401).body(accessTokenResponse);

		}

	}

	/**
	 * Login details like refresh token to be removed.
	 */
	@Override
	public LogoutResponse logout(String deviceId , Integer customerId) {
		LOGGER.info("logoutData "+ deviceId + customerId);
		final String errorMessage = "There is an error in processing your request.";
		if (StringUtils.isEmpty(deviceId) || StringUtils.isBlank(deviceId))
			return LogoutResponse.builder().message(errorMessage).build();
		try {
			CustomerEntity customerEntity = customerEntityRepository.findByEntityId(customerId);
			LOGGER.info("customerEntity "+ customerEntity);
			if(ObjectUtils.isNotEmpty(customerEntity)){
				Set<LoginHistory> remainingLoginDetails = customerEntity.getLoginHistories().stream()
						.filter(his -> {
							boolean isObjectNotEmpty = !ObjectUtils.isEmpty(his);
							boolean isDeviceIdNotEmpty = !StringUtils.isEmpty(his.getDeviceId());
							boolean isDeviceIdNotMatching = !his.getDeviceId().equals(deviceId);
							return isObjectNotEmpty && isDeviceIdNotEmpty && isDeviceIdNotMatching;
						})
						.collect(Collectors.toSet());
				customerEntity.setLoginHistories(remainingLoginDetails);
				customerEntityRepository.save(customerEntity);
				return LogoutResponse.builder().status(true).message("You have been logged out successfully!").build();
			}else{
				return LogoutResponse.builder().message(errorMessage).build();
			}
		} catch (Exception e) {
			return LogoutResponse.builder().message(errorMessage + " " + e.getMessage()).build();
		}
	}
}