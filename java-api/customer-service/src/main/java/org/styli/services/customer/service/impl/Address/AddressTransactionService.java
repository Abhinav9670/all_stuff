package org.styli.services.customer.service.impl.Address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import java.sql.SQLException;

/**
 * Service for handling address save operations in separate transactions.
 * This service ensures that @Transactional annotations work correctly by
 * using Spring's proxy-based transaction management.
 */
@Service
public class AddressTransactionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressTransactionService.class);

    /**
     * Save address in a separate transaction to prevent rollback of outer transaction
     * when emoji encoding errors occur. Uses REQUIRES_NEW to create an isolated transaction.
     * For emoji errors, returns null normally (transaction commits but nothing was saved).
     * For other errors, re-throws to trigger rollback.
     *
     * @param customerAddress the address entity to save
     * @param response the response object to set error status if needed
     * @param customerAddressEntityRepository the repository for address entities
     * @return the saved address entity, or null if emoji encoding error occurred
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CustomerAddressEntity saveAddressInSeparateTransaction(
            CustomerAddressEntity customerAddress,
            CustomerAddreesResponse response,
            CustomerAddressEntityRepository customerAddressEntityRepository) {
        if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
            try {
                return customerAddressEntityRepository.saveAndFlush(customerAddress);
            } catch (DataAccessException e) {
                // Suppress error logging for emoji encoding issues (MySQL utf8 charset limitation)
                // Note: The proper long-term solution is to alter the database, tables, and columns
                // to use utf8mb4 character set instead of utf8 (which is an alias for utf8mb3 in MySQL).
                // This would properly support 4-byte UTF-8 characters (like emojis) and prevent this error.
                if (e.getCause() instanceof SQLException) {
                    SQLException sqlEx = (SQLException) e.getCause();
                    // Check SQLState and error code instead of message string for robustness
                    // MySQL: Incorrect string value = SQLState "HY000" and error code 1366
                    String sqlState = sqlEx.getSQLState();
                    int errorCode = sqlEx.getErrorCode();
                    if ("HY000".equals(sqlState) && errorCode == 1366) {
                        // Return error response without logging
                        // Transaction will commit normally (but nothing was saved, so this is safe)
                        LOGGER.info("[enableCustomerServiceErrorHandling] Emoji encoding error suppressed for address save. Customer will receive error response.");
                        response.setStatus(false);
                        response.setStatusCode("500");
                        response.setStatusMsg("Unable to save address. Please remove special characters.");
                        return null;
                    }
                }
                // Re-throw other DataAccessExceptions to trigger rollback
                throw e;
            }
        } else {
            return customerAddressEntityRepository.saveAndFlush(customerAddress);
        }
    }
}

