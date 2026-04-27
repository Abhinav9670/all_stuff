package org.styli.services.customer.utility.helper;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerProfileResponse;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;

/**
 * Utility class for address nudge functionality
 * Handles logic for showing nudges to users for address compliance
 */
public class AddressNudgeUtility {

    private static final Log LOGGER = LogFactory.getLog(AddressNudgeUtility.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private AddressNudgeUtility() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Determines if nudge should be shown for a user and address
     * 
     * @param user CustomerEntity - the user
     * @param address CustomerAddrees - the address to check
     * @param frequencyDays int - frequency in days (from consul config, default 5)
     * @return true if nudge should be shown, false otherwise
     */
    public static boolean shouldShowNudge(CustomerEntity user, CustomerAddrees address, int frequencyDays) {
        if (!isNudgeEnabled()) {
            return false;
        }

        if (!isValidKSAUser(user)) {
            return false;
        }

        if (!isValidAddressForNudge(address)) {
            return false;
        }

        return checkFrequencyRequirement(user, frequencyDays);
    }

    /**
     * Checks if address nudge feature is enabled
     */
    private static boolean isNudgeEnabled() {
        if (!ServiceConfigs.isAddressNudgeEnabled()) {
            LOGGER.debug("Address nudge is disabled in config");
            return false;
        }
        return true;
    }

    /**
     * Checks if user is valid and from KSA
     */
    private static boolean isValidKSAUser(CustomerEntity user) {
        if (user == null || user.getStoreId() == null) {
            LOGGER.debug("User or storeId is null");
            return false;
        }
        
        List<Integer> ksaStoreIds = Constants.getStoreIdsByCountryCode("966");
        if (ksaStoreIds == null || !ksaStoreIds.contains(user.getStoreId())) {
            LOGGER.debug("User is not from KSA store. StoreId: " + user.getStoreId());
            return false;
        }
        return true;
    }

    /**
     * Checks if address is valid and missing shortAddress
     */
    private static boolean isValidAddressForNudge(CustomerAddrees address) {
        if (address == null) {
            LOGGER.debug("Address is null");
            return false;
        }

        if (StringUtils.isNotBlank(address.getShortAddress())) {
            LOGGER.debug("Short address is already provided: " + address.getShortAddress());
            return false;
        }
        return true;
    }

    /**
     * Checks if frequency requirement is met for showing nudge
     */
    private static boolean checkFrequencyRequirement(CustomerEntity user, int frequencyDays) {
        if (user.getNudgeSeenTime() == null) {
            LOGGER.debug("Last shown date is null, showing nudge");
            return true;
        }

        long daysSinceLastShown = (System.currentTimeMillis() - user.getNudgeSeenTime().getTime()) / (1000 * 60 * 60 * 24);
        
        if (daysSinceLastShown >= frequencyDays) {
            LOGGER.debug("Days since last shown (" + daysSinceLastShown + ") >= frequency (" + frequencyDays + "), showing nudge");
            return true;
        }

        LOGGER.debug("Days since last shown (" + daysSinceLastShown + ") < frequency (" + frequencyDays + "), not showing nudge");
        return false;
    }

    /**
     * Updates the isAddressCompliance flag for a customer based on all their active SA addresses.
     * This method checks only active addresses with countryId == "SA" and sets isAddressCompliance 
     * to true only if ALL active SA addresses are compliant (have shortAddress OR all three fields:
     * streetAddress, unitNumber, and postalCode).
     * Returns true if all active SA addresses are compliant, or if there are no active SA addresses.
     * 
     * @param customerEntity The customer entity to update
     * @param addressEntities List of all address entities for the customer
     * @param client Client service to save the customer entity
     * @return true if all SA addresses are compliant (or no SA addresses exist), false otherwise
     */
    public static boolean updateAddressComplianceStatus(CustomerEntity customerEntity, 
            List<CustomerAddressEntity> addressEntities,
            Client client) {
        try {
            if (customerEntity == null) {
                LOGGER.warn("CustomerEntity is null, cannot update address compliance status");
                return false;
            }

            if (addressEntities == null || addressEntities.isEmpty()) {
                handleNoAddressesCase(customerEntity, client);
                return false;
            }

            // Check all active addresses for compliance
            boolean allCompliant = checkAllAddressesCompliant(addressEntities);

            // Set isAddressCompliance flag based on compliance status
            customerEntity.setIsAddressCompliance(allCompliant);
            saveCustomerEntityIfNeeded(customerEntity, client);
            
            LOGGER.info("Address compliance updated for customer " + customerEntity.getEntityId() + 
                    ": isAddressCompliance=" + allCompliant);
            
            return allCompliant;
        } catch (Exception e) {
            LOGGER.error("Error updating address compliance status for customer " + 
                    (customerEntity != null ? customerEntity.getEntityId() : "null") + ": ", e);
            return false;
        }
    }

    /**
     * Handles the case when there are no addresses - sets compliance to false
     */
    private static void handleNoAddressesCase(CustomerEntity customerEntity, 
            Client client) {
        customerEntity.setIsAddressCompliance(false);
        saveCustomerEntityIfNeeded(customerEntity, client);
        LOGGER.info("No addresses found for customer " + customerEntity.getEntityId() + 
                ", setting isAddressCompliance to false");
    }

    /**
     * Checks if all active SA addresses are compliant.
     * Only checks addresses with countryId == "SA".
     * Returns true if all active SA addresses are compliant, or if there are no active SA addresses.
     * Returns false if any active SA address is non-compliant.
     */
    private static boolean checkAllAddressesCompliant(
            List<CustomerAddressEntity> addressEntities) {
            for (CustomerAddressEntity addressEntity : addressEntities) {
                // Only check compliance for active SA addresses
                if (isActiveAddress(addressEntity) && isSACountry(addressEntity) && !isCompliantAddress(addressEntity)) {
                    return false;
                }
            }
        return true;
    }

    /**
     * Checks if an address is active
     */
    private static boolean isActiveAddress(CustomerAddressEntity addressEntity) {
        return addressEntity.getIsActive() != null && addressEntity.getIsActive().equals(1);
    }

    /**
     * Checks if an address is from Saudi Arabia (SA)
     */
    private static boolean isSACountry(CustomerAddressEntity addressEntity) {
        return "SA".equalsIgnoreCase(addressEntity.getCountryId());
    }

    /**
     * Checks if an address is compliant using new logic:
     * - shortAddress is present, OR
     * - All three fields (streetAddress, unitNumber, and postalCode) are present
     */
    private static boolean isCompliantAddress(CustomerAddressEntity addressEntity) {
        // Check if shortAddress is present
        if (StringUtils.isNotBlank(addressEntity.getShortAddress())) {
            return true;
        }
        
        // Extract streetAddress from street field
        String streetAddress = extractStreetAddress(addressEntity.getStreet());
        
        // Check if all three fields (streetAddress, unitNumber, and postalCode) are present
        return StringUtils.isNotBlank(streetAddress) 
            && StringUtils.isNotBlank(addressEntity.getUnitNumber()) 
            && StringUtils.isNotBlank(addressEntity.getPostalCode());
    }

    /**
     * Saves customer entity if client is not null
     */
    private static void saveCustomerEntityIfNeeded(CustomerEntity customerEntity, 
            Client client) {
        if (client != null) {
            client.saveAndFlushCustomerEntity(customerEntity);
        }
    }

    /**
     * Checks address compliance and sets addressComplianceShowNudge flag
     * Also sets isAddressCompliance flag in customer_entity and ksaAddressCompliant in response
     * 
     * @param customerEntity The customer entity
     * @param client Client service for database operations
     * @param responseBody Response body to set the nudge flag and ksaAddressCompliant
     * @param addressRepository Repository to fetch addresses
     */
    public static void checkAddressComplianceAndSetNudge(CustomerEntity customerEntity, 
            Client client,
            CustomerProfileResponse responseBody, 
            CustomerAddressEntityRepository addressRepository) {
        try {
            // Get all addresses for the customer
            if (addressRepository == null) {
                LOGGER.warn("CustomerAddressEntityRepository is null, skipping address compliance check");
                responseBody.setShowAddressComplianceNudge(false);
                responseBody.setKsaAddressCompliant(false);
                return;
            }

            List<CustomerAddressEntity> addressEntities = 
                    addressRepository.findAllByCustomerId(customerEntity.getEntityId());
            
            // Update address compliance status using the reusable utility method
            // The return value indicates if all addresses are compliant
            boolean ksaAddressCompliant = updateAddressComplianceStatus(customerEntity, addressEntities, client);
            responseBody.setKsaAddressCompliant(ksaAddressCompliant);

            // Convert to CustomerAddrees list for nudge checking
            List<CustomerAddrees> addresses = convertToCustomerAddreesListForNudge(addressEntities);

            // Determine if nudge should be shown based on new conditions
            // Only show nudge if addresses are not compliant
            boolean showAddressComplianceNudge = !ksaAddressCompliant && shouldShowAddressComplianceNudge(customerEntity, addresses);
            responseBody.setShowAddressComplianceNudge(showAddressComplianceNudge);

        } catch (Exception e) {
            LOGGER.error("Error checking address compliance: ", e);
            responseBody.setShowAddressComplianceNudge(false);
            responseBody.setKsaAddressCompliant(false);
        }
    }

    /**
     * Converts CustomerAddressEntity list to CustomerAddrees list for nudge checking.
     * Only extracts the fields needed for nudge checking: unitNumber, postalCode, streetAddress, shortAddress.
     * Filters only active addresses.
     * 
     * @param addressEntities List of CustomerAddressEntity
     * @return List of CustomerAddrees with only nudge-relevant fields populated
     */
    private static List<CustomerAddrees> convertToCustomerAddreesListForNudge(
            List<org.styli.services.customer.pojo.address.response.CustomerAddressEntity> addressEntities) {
        List<CustomerAddrees> addresses = new ArrayList<>();
        for (org.styli.services.customer.pojo.address.response.CustomerAddressEntity addressEntity : addressEntities) {
            if (addressEntity.getIsActive() != null && addressEntity.getIsActive().equals(1)) {
                CustomerAddrees address = createCustomerAddreesFromEntity(addressEntity);
                addresses.add(address);
            }
        }
        return addresses;
    }

    /**
     * Creates a CustomerAddrees object from CustomerAddressEntity with only nudge-relevant fields.
     * 
     * @param addressEntity The source address entity
     * @return CustomerAddrees with nudge-relevant fields populated
     */
    private static CustomerAddrees createCustomerAddreesFromEntity(CustomerAddressEntity addressEntity) {
        CustomerAddrees address = new CustomerAddrees();
        
        // Extract only the fields needed for nudge checking
        address.setUnitNumber(addressEntity.getUnitNumber());
        address.setPostalCode(addressEntity.getPostalCode());
        address.setShortAddress(addressEntity.getShortAddress());
        
        // Extract streetAddress from getStreet() if available
        String streetAddress = extractStreetAddress(addressEntity.getStreet());
        if (streetAddress != null) {
            address.setStreetAddress(streetAddress);
        }
        
        return address;
    }

    /**
     * Extracts street address from the street field by splitting on newline and taking the second part.
     * 
     * @param street The street field from the address entity
     * @return The street address (second part after split) or null if not available
     */
    private static String extractStreetAddress(String street) {
        if (street == null || !ArrayUtils.isNotEmpty(street.split("\n"))) {
            return null;
        }
        
        String[] streetParts = street.split("\n");
        if (streetParts.length > 1) {
            return streetParts[1];
        }
        
        return null;
    }

    /**
     * Determines if address compliance nudge should be shown based on conditions:
     * 1. If unitNumber, postalCode and streetAddress are present but shortAddress is not present → false
     * 2. If shortAddress is present but any/all of (unitNumber, postalCode, streetAddress) are absent → false
     * 3. If unitNumber, postalCode, streetAddress are ALL absent AND shortAddress is also absent → true
     * 
     * @param customerEntity The customer entity
     * @param addresses List of addresses to check
     * @return true if nudge should be shown, false otherwise
     */
    private static boolean shouldShowAddressComplianceNudge(CustomerEntity customerEntity, List<CustomerAddrees> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return false;
        }

        int frequencyDays = ServiceConfigs.getAddressNudgeFrequencyDays();
        for (CustomerAddrees address : addresses) {
            if (shouldShowNudgeForAddress(customerEntity, address, frequencyDays)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if nudge should be shown for a specific address.
     * Condition 3: All four fields (unitNumber, postalCode, streetAddress, shortAddress) are absent → show nudge
     * Conditions 1 & 2: In all other cases, nudge should not be shown (false)
     * 
     * @param customerEntity The customer entity
     * @param address The address to check
     * @param frequencyDays Frequency in days for showing nudge
     * @return true if nudge should be shown for this address, false otherwise
     */
    private static boolean shouldShowNudgeForAddress(CustomerEntity customerEntity, CustomerAddrees address, int frequencyDays) {
        boolean hasUnitNumber = StringUtils.isNotBlank(address.getUnitNumber());
        boolean hasPostalCode = StringUtils.isNotBlank(address.getPostalCode());
        boolean hasStreetAddress = StringUtils.isNotBlank(address.getStreetAddress());
        boolean hasShortAddress = StringUtils.isNotBlank(address.getShortAddress());
        // Condition 3: All four fields (unitNumber, postalCode, streetAddress, shortAddress) are absent → show nudge
        boolean isMissingRequiredFields = !hasUnitNumber || !hasPostalCode || !hasStreetAddress;
        if (isMissingRequiredFields && !hasShortAddress) {
            return shouldShowNudge(customerEntity, address, frequencyDays);
        }
        // Conditions 1 & 2: In all other cases, nudge should not be shown (false)
        return false;
    }
}

