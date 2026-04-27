package org.styli.services.customer.helper;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFuture;
import org.styli.services.customer.model.CustomerGridFlat;
import org.styli.services.customer.model.DeleteCustomersEventsEntity;
import org.styli.services.customer.pojo.DeleteCustomerEntity;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.account.CustomerStatus;
import org.styli.services.customer.pojo.consul.DeleteCustomer;
import org.styli.services.customer.pojo.kafka.CustomerDeleteObject;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.Customer.*;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 10/06/2022 - 12:40 PM
 */

@Component
public class AccountHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountHelper.class);

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    @Autowired
    RedisHelper redisHelper;

    @Autowired
    DeleteCustomerEntityRepository deleteCustomerEntityRepository;

    @Autowired
    CustomerGridFlatRepository customerGridFlatRepository;

    @Autowired
    CustomerAddressEntityRepository customerAddressEntityRepository;

    @Autowired
    CustomerEntityRepository customerEntityRepository;

    @Autowired
    DeleteCustomersEventsRepository deleteCustomersEventsRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${customer.kafka.customer_delete_topic}")
    private String customerDeleteTopic;

    @Value("${env}")
    private String env;

    private static String getLanguageCode(Stores store, String fallback) {
        String result = fallback;
        try {
            if (store != null && StringUtils.isNotEmpty(store.getStoreLanguage())) {
                result = store.getStoreLanguage().split("_")[0];
            }
        } catch (Exception e) {
            result = fallback;
        }
        return result;
    }

    public OtpBucketObject getBucketObject(String deleteCustomerOtpCacheName, String customerId) {
        if (ObjectUtils.isEmpty(customerId))
            return null;
        OtpBucketObject otpBucketObject = null;
        try {
            otpBucketObject = (OtpBucketObject) redisHelper.get(deleteCustomerOtpCacheName, customerId,
                    OtpBucketObject.class);
            if (ObjectUtils.isNotEmpty(otpBucketObject))
                otpBucketObject.setCustomerId(customerId);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        return otpBucketObject;
    }

    public String generateSafeOtp(OtpBucketObject bucketObject, long now) {
        if (!ServiceConfigs.forceResetOtp() && bucketObject != null
                && StringUtils.isNotEmpty(bucketObject.getOtp())
                && (bucketObject.getExpiresAt() == null || now <= bucketObject.getExpiresAt())) {
            LOGGER.info("generateSafeOtp(): using old otp!");
            return bucketObject.getOtp();
        }

        LOGGER.info("generateSafeOtp(): creating new otp!");
        return SmsHelper.generateOtp(getOtpLength());
    }

    public int getOtpLength() {
        return ServiceConfigs.getOtpLength();
    }

    public String getOtpMessage(String langCode) {
        String message = "";
        try {
            if (langCode.equals("en"))
                message = ServiceConfigs.getDeleteCustomer().getOtpMessage().getEn();
            else if (langCode.equals("ar"))
                message = ServiceConfigs.getDeleteCustomer().getOtpMessage().getAr();
        } catch (Exception e) {
            LOGGER.info("getOtpMessage error!");
            LOGGER.error("Error in getOtpMessage", e);
        }
        return message;
    }

    public String getEmailSubject(String langCode) {
        String message = "";
        try {
            if (langCode.equals("en"))
                message = ServiceConfigs.getDeleteCustomer().getEmail().getSubject().getEn();
            else if (langCode.equals("ar"))
                message = ServiceConfigs.getDeleteCustomer().getEmail().getSubject().getAr();
        } catch (Exception e) {
            LOGGER.info("getEmailSubject error!");
            LOGGER.error("Error in getEmailSubject", e);
        }
        return message;
    }

    public String getEmailMessage(String langCode) {
        String message = "";
        try {
            if (langCode.equals("en"))
                message = ServiceConfigs.getDeleteCustomer().getEmail().getContent().getEn();
            else if (langCode.equals("ar"))
                message = ServiceConfigs.getDeleteCustomer().getEmail().getContent().getAr();
        } catch (Exception e) {
            LOGGER.info("getEmailMessage error!");
            LOGGER.error("Error in getEmailMessage", e);
        }
        return message;
    }

    public List<DeleteCustomerEntity> getDeleteRequestsForCleanup() {
        List<DeleteCustomerEntity> requests = new ArrayList<>();
        try {
            requests = deleteCustomerEntityRepository.findAllByCronProcessedAndCompletedAt(
                    1,
                    null);
        } catch (Exception e) {
            LOGGER.info("Could not fetch delete requests!");
            LOGGER.error(e.getMessage());
        }
        return requests;
    }

    public List<DeleteCustomerEntity> getDeleteRequests() {
        List<DeleteCustomerEntity> requests = new ArrayList<>();
        try {
            requests = deleteCustomerEntityRepository.findAllByTtlTimeLessThanAndMarkedForDeleteAndCronProcessedNot(
                    new Date(),
                    1,
                    1);
        } catch (Exception e) {
            LOGGER.info("Could not fetch delete requests!");
            LOGGER.error(e.getMessage());
        }
        return requests;
    }

    public void handleCustomerGridFlat(String email, CustomerEntity customer) {
        CustomerGridFlat customerGridFlat = customerGridFlatRepository.findByEntityId(customer.getEntityId());
        LOGGER.info("processCustomerDelete-handleCustomerGridFlat start on AccountHelper Handller");
        if (ObjectUtils.isNotEmpty(customerGridFlat)) {
            customerGridFlat.setName(null);
            customerGridFlat.setEmail(email);
            customerGridFlat.setPhoneNumber(null);
            customerGridFlat.setShippingFull(null);

            customerGridFlat.setBillingFull(null);
            customerGridFlat.setBillingFirstname(null);
            customerGridFlat.setBillingLastname(null);
            customerGridFlat.setBillingTelephone(null);
            customerGridFlat.setBillingPostcode(null);
            customerGridFlat.setBillingCountryId(null);
            customerGridFlat.setBillingRegion(null);
            customerGridFlat.setBillingStreet(null);
            customerGridFlat.setBillingCity(null);
            customerGridFlat.setBillingFax(null);
            customerGridFlat.setBillingVatId(null);
            customerGridFlat.setBillingCompany(null);
            customerGridFlatRepository.save(customerGridFlat);
        }
        LOGGER.info("processCustomerDelete-handleCustomerGridFlat end on AccountHelper Handller");
    }

    public void handleCustomerAddressEntity(CustomerEntity customer) {
        LOGGER.info("processCustomerDelete-handleCustomerAddressEntity start on AccountHelper Handller");
        customerAddressEntityRepository.deleteByParentId(customer.getEntityId());
        LOGGER.info("processCustomerDelete-handleCustomerAddressEntity end on AccountHelper Handller");
    }

    public void handleCustomerEntity(String email, CustomerEntity customer) {
        LOGGER.info("processCustomerDelete-handleCustomerEntity start on AccountHelper Handller");
        customer.setEmail(email);
        customer.setFirstName(null);
        customer.setLastName(null);
        customer.setPhoneNumber(null);
        customer.setIsActive(CustomerStatus.DELETED.getValue());
        customerEntityRepository.save(customer);
        LOGGER.info("processCustomerDelete-handleCustomerEntity end on AccountHelper Handller");
    }

    public void handleKafkaPush(CustomerEntity customer) {
        CustomerDeleteObject customerDeleteObject = new CustomerDeleteObject();
        customerDeleteObject.setCustomerId(customer.getEntityId());
        kafkaTemplate.send(customerDeleteTopic, customerDeleteObject);
    }

    public void handleDeleteCustomerEntity(List<DeleteCustomerEntity> requests, CustomerEntity customer) {
        LOGGER.info("processCustomerDelete-handleDeleteCustomerEntity start on AccountHelper Handller");
        DeleteCustomerEntity deleteCustomerEntity = requests.stream()
                .filter(e -> e.getCustomerId().equals(customer.getEntityId()))
                .findFirst()
                .orElse(null);

        Objects.requireNonNull(deleteCustomerEntity).setCronProcessed(1);
        deleteCustomerEntityRepository.saveAndFlush(deleteCustomerEntity);
        LOGGER.info("processCustomerDelete-handleDeleteCustomerEntity end on AccountHelper Handller");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleDeleteCustomerEventsRows(CustomerEntity customer) {
        LOGGER.info("Delete Customer : processCustomerDelete-handleDeleteCustomerEventsRows START for customerId: " +customer.getEntityId());
        // Ensure customer exists before proceeding
        Optional<CustomerEntity> existingCustomer = customerEntityRepository.findById(customer.getEntityId());
        if (existingCustomer.isEmpty()) {
            LOGGER.info("Delete Customer : Customer ID does not exist! Aborting delete event creation." +customer.getEntityId());
            return;
        }

        DeleteCustomer deleteCustomer = ServiceConfigs.getDeleteCustomer();
        if (deleteCustomer == null || deleteCustomer.getTasks().isEmpty()) {
            LOGGER.info("Delete Customer : No delete tasks found for customerId: " + customer.getEntityId());
            return;
        }

        try {
            List<String> tasks = deleteCustomer.getTasks();
            // Fetch existing events once before the loop to avoid N+1 query problem
            List<DeleteCustomersEventsEntity> existingEvents = deleteCustomersEventsRepository.findByCustomerId(customer.getEntityId());
            Set<String> existingTasks = existingEvents.stream()
                    .map(DeleteCustomersEventsEntity::getTask)
                    .collect(Collectors.toSet());
            
            for (String task : tasks) {
                // Check if event already exists to avoid duplicate key issues
                if (existingTasks.contains(task)) {
                    LOGGER.info("Delete Customer : Event already exists for customerId=" + customer.getEntityId() + ", task=" + task + ". Skipping creation.");
                    continue;
                }
                
                DeleteCustomersEventsEntity deleteCustomersEventsEntity = new DeleteCustomersEventsEntity();
                deleteCustomersEventsEntity.setCustomerId(customer.getEntityId());
                deleteCustomersEventsEntity.setStatus(0);
                deleteCustomersEventsEntity.setCreatedAt(new Date());
                deleteCustomersEventsEntity.setUpdatedAt(new Date());
                deleteCustomersEventsEntity.setTask(task);

                LOGGER.info("Delete Customer : Creating delete event: customerId={}, task={}", customer.getEntityId(), task);
                
                // Wrap save operation in try-catch to handle foreign key constraint errors gracefully
                if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                    try {
                        deleteCustomersEventsRepository.saveAndFlush(deleteCustomersEventsEntity);
                    } catch (DataIntegrityViolationException saveException) {
                        // Check if this is a foreign key constraint violation
                        Throwable rootCause = saveException.getRootCause();
                        if (rootCause instanceof SQLIntegrityConstraintViolationException) {
                            // This is a more specific and robust way to check for foreign key constraint violations
                            // This can happen if customer was deleted between check and insert, or event already exists
                            LOGGER.info("[enableCustomerServiceErrorHandling] Delete Customer : Foreign key constraint violation while inserting delete event for customerId: " +customer.getEntityId() + " error " +rootCause.getMessage());
                            continue; // Skip to next task
                        }
                        // Re-throw if it's not a foreign key constraint error
                        throw saveException;
                    }
                } else {
                    deleteCustomersEventsRepository.saveAndFlush(deleteCustomersEventsEntity);
                }
            }
        } catch (DataIntegrityViolationException e) {
            // Catch any remaining Spring's wrapped exception (includes SQLIntegrityConstraintViolationException)
            if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                Throwable rootCause = e.getRootCause();
                String errorMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();
                if (rootCause != null && rootCause.getClass().getSimpleName().contains("SQLIntegrityConstraintViolationException")) {
                    LOGGER.info("[enableCustomerServiceErrorHandling] Delete Customer : Foreign key constraint violation while inserting delete event for customerId: {}. Customer may not exist or event already exists. Error: {}", customer.getEntityId(), errorMessage);
                } else {
                    LOGGER.info("[enableCustomerServiceErrorHandling] Delete Customer : Data integrity violation while inserting delete event for customerId: {}. Error: {}", customer.getEntityId(), errorMessage);
                }
            } else {
                throw e;
            }
        } catch (Exception e) {
            if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
                LOGGER.info("[enableCustomerServiceErrorHandling] Delete Customer : Unexpected error while handling delete events for customerId: {}. Error: {}", customer.getEntityId(), e.getMessage());
            } else {
                throw e;
            }
        }

        LOGGER.info("Delete Customer : processCustomerDelete-handleDeleteCustomerEventsRows END for customerId: {}", customer.getEntityId());
    }

}
