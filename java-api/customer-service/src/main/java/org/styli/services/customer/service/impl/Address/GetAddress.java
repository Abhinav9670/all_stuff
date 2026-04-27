package org.styli.services.customer.service.impl.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.styli.services.customer.exception.BadRequestException;
import org.styli.services.customer.helper.NationalIdHelper;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddressBody;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntityVarchar;
import org.styli.services.customer.pojo.address.response.NationalId;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.impl.CustomerV4ServiceImpl;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.UtilityConstant;
import org.styli.services.customer.utility.UtilityCustomerConatant;

@Component
public class GetAddress {
	
    @Value("${customer.jwt.flag}")
    String jwtFlag;

    @Autowired
    private NationalIdHelper nationalIdHelper;
    
    private static final Log LOGGER = LogFactory.getLog(CustomerV4ServiceImpl.class);

    public CustomerAddreesResponse get(Integer customerId, CustomerEntityRepository customerEntityRepository,
            CustomerAddressEntityRepository customerAddressEntityRepository, StaticComponents staticComponents
            ,Map<String, String> requestHeader , String jwtFlag,Client client) {
    	
        CustomerAddreesResponse response = new CustomerAddreesResponse();
        List<CustomerAddressEntity> customerAddressList = null;
        CustomerEntity customer = null;
        CustomerAddressBody responseBody = new CustomerAddressBody();
        try {

            if (!customerEntityRepository.existsById(customerId)) {

                response.setStatus(false);
                response.setStatusCode("201");
                response.setStatusMsg("Invalid Customer ID");

                return response;
            }

            customerAddressList = customerAddressEntityRepository.findAllByCustomerId(customerId);
            customer = customerEntityRepository.findByEntityId(customerId);
            
            
            String appSource = requestHeader.get(UtilityConstant.APP_HEADER_APP_SOURCE);
            String appVersion = requestHeader.get(UtilityConstant.APP_HEADER_APP_VERSION);
            
            if (StringUtils.isNotBlank(appSource) && StringUtils.isNotBlank(appVersion)
    				&& UtilityConstant.APPSOURCELIST.contains(appSource)) {

    			LOGGER.info("APP version:" + appVersion);

    			String appVersionInInt = appVersion.replace(".", "");

    			LOGGER.info("truncated app version number:" + appVersion);

    			if (StringUtils.isNumeric(appVersionInInt)
    					&& Integer.valueOf(appVersionInInt) >= UtilityConstant.APP_VERSION_NUMBER) {
    				
    				if ("1".equals(jwtFlag) && null == customer.getJwtToken()) {

    					throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
    					
    				}if ("1".equals(jwtFlag) && (null == customer.getJwtToken()
    						|| (null != customer.getJwtToken() && customer.getJwtToken().equals(0)))) {

    					LOGGER.info("JWT token not changed with zero:" + customer.getEntityId());

    					throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

    				}
    				
    			}

    		}
            
            if (CollectionUtils.isNotEmpty(customerAddressList)) {

                List<CustomerAddrees> addressList = new ArrayList<>();

                for (CustomerAddressEntity addressEntity : customerAddressList) {

                    if (null != addressEntity.getIsActive() && addressEntity.getIsActive().equals(1)) {

                        CustomerAddrees address = setCustomerAddress(addressEntity, customer.getDefaultShipping(),
                                customerAddressEntityRepository, staticComponents);
                        if(StringUtils.isNotEmpty(address.getMobileNumber()) &&
                                StringUtils.isNotBlank(address.getMobileNumber()) &&
                        StringUtils.isNotEmpty(customer.getPhoneNumber()) &&
                                StringUtils.isNotBlank(customer.getPhoneNumber()) &&
                                address.getMobileNumber().equalsIgnoreCase(customer.getPhoneNumber())
                                && null!=customer.getIsMobileVerified()
                        && customer.getIsMobileVerified()) {
                            address.setIsMobileVerified(true);
                            addressEntity.setIsMobileVerified(1);
                            client.saveAndFlushAddressEntity(addressEntity);
                        }
                        
                        // Set ksaAddressCompliant for individual address based on new conditions
                        Boolean addressCompliant = isKsaAddressCompliant(address);
                        address.setKsaAddressCompliant(addressCompliant);
                        
                        addressList.add(address);
                    }

                }

                responseBody.setAddresses(addressList);

                // Set ksaAddressCompliant at parent level: 
                // true only if all active SA addresses are compliant
                // null if no SA addresses exist
                // false if any SA address is non-compliant
                Boolean parentKsaAddressCompliant = null;
                boolean hasSAActiveAddresses = false;
                for (CustomerAddrees addr : addressList) {
                    if ("SA".equalsIgnoreCase(addr.getCountry())) {
                        hasSAActiveAddresses = true;
                        if (addr.getKsaAddressCompliant() != null && !addr.getKsaAddressCompliant()) {
                            parentKsaAddressCompliant = false;
                            break;
                        }
                    }
                }
                if (hasSAActiveAddresses && parentKsaAddressCompliant == null) {
                    // All SA addresses are compliant
                    parentKsaAddressCompliant = true;
                }
                responseBody.setKsaAddressCompliant(parentKsaAddressCompliant);

                response.setResponse(responseBody);
                response.setStatus(true);
                response.setStatusCode("200");
                response.setStatusMsg("SUCCESS");
            } else {

                response.setStatus(true);
                response.setStatusCode("201");
                response.setStatusMsg("No Address Found");
            }

        } catch (DataAccessException exception) {

            ErrorType error = new ErrorType();

            error.setErrorCode("400");
            error.setErrorMessage(exception.getMessage());

            response.setStatus(false);
            response.setStatusCode("204");
            response.setStatusMsg("ERROR");
            response.setError(error);

        }
        return response;

    }

    public CustomerAddreesResponse getById(Integer addressId, Integer customerId,
            CustomerEntityRepository customerEntityRepository,
            CustomerAddressEntityRepository customerAddressEntityRepository, StaticComponents staticComponents) {
        CustomerAddreesResponse response = new CustomerAddreesResponse();
        CustomerAddressBody responseBody = new CustomerAddressBody();

        CustomerEntity customerObject = customerEntityRepository.findByEntityId(customerId);
        CustomerAddressEntity addressEntity = customerAddressEntityRepository.findByEntityId(addressId);

        if (addressEntity != null) {
            CustomerAddrees address = setCustomerAddress(addressEntity, customerObject.getDefaultShipping(),
                    customerAddressEntityRepository, staticComponents);

            responseBody.setAddress(address);
            
            // Set ksaAddressCompliant at parent level for single address
            Boolean addressCompliant = isKsaAddressCompliant(address);
            responseBody.setKsaAddressCompliant(addressCompliant);

            response.setResponse(responseBody);
            response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg("SUCCESS");

            return response;

        } else {
            response.setStatus(false);
            response.setStatusCode("201");
            response.setStatusMsg("No Address found!");

            return response;
        }
    }

    /**
     * @param addressEntity
     * @param shiftingAddressId
     * @return CustomerAddrees
     */
    private CustomerAddrees setCustomerAddress(CustomerAddressEntity addressEntity, Integer shiftingAddressId,
            CustomerAddressEntityRepository customerAddressEntityRepository, StaticComponents staticComponents) {

        CustomerAddrees address = new CustomerAddrees();

        address.setCustomerId(addressEntity.getParentId());
        address.setAddressId(addressEntity.getEntityId());
        address.setFirstName(addressEntity.getFirstname());
        address.setLastName(addressEntity.getLastName());
        address.setCountry(addressEntity.getCountryId());
        address.setCity(addressEntity.getCity());
        address.setRegion(addressEntity.getRegion());
        address.setRegionId(addressEntity.getRegionId());
        
        address.setLatitude(addressEntity.getLatitude());
        address.setLongitude(addressEntity.getLongitude());
        address.setFormattedAddress(addressEntity.getFormattedAddress());
        address.setIsMobileVerified(false);
        if(addressEntity.getIsMobileVerified() != null) {
            address.setIsMobileVerified(addressEntity.getIsMobileVerified() == 1);
        }
        setBuildingOrStreet(addressEntity, address);

        CustomerAddressEntityVarchar area = null;
        CustomerAddressEntityVarchar landMark = null;

        setMobileNumber(addressEntity, customerAddressEntityRepository, address);

        Set<CustomerAddressEntityVarchar> addressVarList = addressEntity.getCustomerAddressEntityVarchar();

        if (null != staticComponents.getAttrMap()) {

            Integer areaAttributeId = staticComponents.getAttrMap().entrySet().stream()
                    .filter(e -> e.getValue().equals(UtilityCustomerConatant.CUSTOMER_AREA_ATTRIBUTE))
                    .map(Map.Entry::getKey).findFirst().orElse(0);

            Integer landmarkAttributeId = staticComponents.getAttrMap().entrySet().stream()
                    .filter(e -> e.getValue().equals(UtilityCustomerConatant.CUSTOMER_LANDMARK_ATTRIBUTE))
                    .map(Map.Entry::getKey).findFirst().orElse(0);

            if (null != addressVarList) {
                area = addressVarList.stream()
                        .filter(x -> null != x.getAttributeId() && areaAttributeId.equals(x.getAttributeId())).findAny()
                        .orElse(null);

                landMark = addressVarList.stream()
                        .filter(x -> null != x.getAttributeId() && landmarkAttributeId.equals(x.getAttributeId()))
                        .findAny().orElse(null);
            }
        }

        setAreaLandmarkInAddress(addressEntity, shiftingAddressId, address, area, landMark);

        // Set new national address format fields
        address.setUnitNumber(addressEntity.getUnitNumber());
        address.setPostalCode(addressEntity.getPostalCode());
        address.setShortAddress(addressEntity.getShortAddress());

        setNationalIdIfPresent(addressEntity, address);

        // Note: ksaAddressCompliant is now set in the calling method (get() or getById())
        // after all fields are populated, to ensure streetAddress is available

        return address;
    }

    private void setNationalIdIfPresent(CustomerAddressEntity addressEntity, CustomerAddrees address) {
        if (addressEntity.getNationalIdType() == null && addressEntity.getNationalIdNumber() == null
                && addressEntity.getNationalIdImageData() == null && addressEntity.getNationalIdExpirationDate() == null) {
            return;
        }
        NationalId nationalId = new NationalId();
        nationalId.setType(addressEntity.getNationalIdType());
        nationalId.setFilePath(addressEntity.getNationalIdImageData() != null
                ? nationalIdHelper.decryptNationalIdDetails(addressEntity.getNationalIdImageData()) : null);
        String number = addressEntity.getNationalIdNumber();
        nationalId.setNumber(nationalIdHelper != null ? nationalIdHelper.decryptNationalIdDetails(number) : number);
        nationalId.setExpirationDate(addressEntity.getNationalIdExpirationDate());
        address.setNationalId(nationalId);
    }

	private void setAreaLandmarkInAddress(CustomerAddressEntity addressEntity, Integer shiftingAddressId,
			CustomerAddrees address, CustomerAddressEntityVarchar area, CustomerAddressEntityVarchar landMark) {
		if (null != area) {

            address.setArea(area.getValue());

        }
        if (null != landMark) {

            address.setLandMark(landMark.getValue());

        }

        if (null != shiftingAddressId && shiftingAddressId.equals(addressEntity.getEntityId())) {

            address.setDefaultAddress(true);

        } else {

            address.setDefaultAddress(false);
        }
	}

	private void setMobileNumber(CustomerAddressEntity addressEntity,
			CustomerAddressEntityRepository customerAddressEntityRepository, CustomerAddrees address) {
		if (StringUtils.isNotBlank(addressEntity.getTelephone())
                && !addressEntity.getTelephone().matches("[+]{1}[0-9]{3}\\s{1}.*")
                && addressEntity.getTelephone().length() > 4) {

            /** This change for those phone does not have space **/

            String countryCode = addressEntity.getTelephone().substring(0, 4);

            String telPhoneNum = addressEntity.getTelephone().substring(4);
            
			if (Objects.nonNull(telPhoneNum)) {
				telPhoneNum = telPhoneNum.trim();
				countryCode = countryCode.trim();
			}

            LOGGER.info("countryCode:" + countryCode);
            LOGGER.info("telPhoneNum:" + telPhoneNum);

            StringBuilder sb = new StringBuilder();

            LOGGER.info(sb.append(countryCode).append(" ").append(telPhoneNum));

            addressEntity.setTelephone(sb.toString());

            customerAddressEntityRepository.saveAndFlush(addressEntity);

            address.setMobileNumber(addressEntity.getTelephone());

        } else {

            address.setMobileNumber(addressEntity.getTelephone());
        }
	}

	private void setBuildingOrStreet(CustomerAddressEntity addressEntity, CustomerAddrees address) {
		if (null != addressEntity.getStreet() && ArrayUtils.isNotEmpty(addressEntity.getStreet().split("\n"))) {

            address.setBuildingNumber(addressEntity.getStreet().split("\n")[0]);

            if (addressEntity.getStreet().split("\n").length > 1) {

                address.setStreetAddress(addressEntity.getStreet().split("\n")[1]);

            }

        }
	}

	/**
	 * Determines KSA address compliance based on the presence of address fields.
	 * Only applicable for addresses with country = "SA".
	 * 
	 * Compliance rules:
	 * 1. If unitNumber, postalCode and streetAddress are present but shortAddress is not present → true
	 * 2. If shortAddress is present but any/all of (unitNumber, postalCode, streetAddress) are absent → true
	 * 3. If unitNumber, postalCode, streetAddress are ALL absent AND shortAddress is also absent → false
	 * 
	 * @param address The address to check for compliance
	 * @return Boolean.TRUE if compliant, Boolean.FALSE if non-compliant, null if not SA country
	 */
	private Boolean isKsaAddressCompliant(CustomerAddrees address) {
		if (!"SA".equalsIgnoreCase(address.getCountry())) {
			return null;
		}

		boolean hasUnitNumber = StringUtils.isNotBlank(address.getUnitNumber());
		boolean hasPostalCode = StringUtils.isNotBlank(address.getPostalCode());
		boolean hasStreetAddress = StringUtils.isNotBlank(address.getStreetAddress());
		boolean hasShortAddress = StringUtils.isNotBlank(address.getShortAddress());

		return (hasUnitNumber && hasPostalCode && hasStreetAddress) || hasShortAddress;
	}

}