package org.styli.services.customer.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.JobException;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;

/**
 * Read Incremental data from MongoDB based on document last updated on and push
 * to BigQuery
 */
@Service
public class BigQuerySyncService {

	private static final Log LOGGER = LogFactory.getLog(BigQuerySyncService.class);
	private static final String DATA_SET = "customer";
	private static final String TABLE_ID = "new_customer_entity_v2";

	@Autowired
	private BigQuery bigQuery;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	@Qualifier("gccMongoTemplate")
	private MongoTemplate mongoGccTemplate;

	private TableId tableId = TableId.of(DATA_SET, TABLE_ID);

	public void incrementalSync() {
		try {
			Integer syncDuration = (Integer)ServiceConfigs.consulServiceMap.get("customerBigQuerySyncDuration");
			syncDuration = Objects.isNull(syncDuration) ? 12 : syncDuration;
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.HOUR, -syncDuration);
			Criteria criteria = Criteria.where("updatedAt").gt(calendar.getTime());
			Query query = Query.query(criteria);
			List<CustomerEntity> customers = mongoGccTemplate.find(query, CustomerEntity.class);

			List<Map<String, Object>> customerRecords = customers.parallelStream().map(CustomerEntity::toMap)
					.collect(Collectors.toList());

			List<String> customerIds = customers.parallelStream().map(cust -> cust.getEntityId().toString())
					.collect(Collectors.toList());
			if(customerIds.isEmpty()) {
				LOGGER.info("Incremental Sync to bigQuery skips no records found.");
				return; 
			}
			deleteRecords(customerIds);
			LOGGER.info("Incremental Sync delete completed. " + customerIds);
			saveRecords(customerRecords);
			LOGGER.info("Incremental Sync to bigQuery completed.");
		} catch (Exception e) {
			LOGGER.error("Error in BigQuery Sync : ", e);
		}
	}

	private void deleteRecords(List<String> customerIds) throws CustomerException {
		StringBuilder builder = new StringBuilder();
		String recordIdsString = "'" + String.join("', '", customerIds) + "'";
		builder.append("DELETE FROM ");
		builder.append(DATA_SET + ".");
		builder.append(TABLE_ID);
		builder.append(" WHERE entityId IN(");
		builder.append(recordIdsString);
		builder.append(")");
		String deleteStatement = builder.toString();
		LOGGER.info("Clean BigQuery statement : " + deleteStatement);
		QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(deleteStatement).setUseLegacySql(false)
				.build();
		try {
			bigQuery.query(queryConfig);
		} catch (JobException | InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.error("Error in deleting from bigQuery. ", e);
			throw new CustomerException("Error in deleting records in BigQuery. Errors : " + e.getMessage());
		}
	}

	public void saveRecords(List<Map<String, Object>> customerRecords) throws CustomerException {
		InsertAllRequest.Builder requestBuilder = InsertAllRequest.newBuilder(tableId);
		customerRecords.parallelStream().forEach(requestBuilder::addRow);
		InsertAllResponse response = bigQuery.insertAll(requestBuilder.build());
		if (response.hasErrors()) {
			throw new CustomerException("Error in saving records to BigQuery. Errors : " + response.getErrorsFor(0));
		}
	}

	public void completeSync() {
		try {
			LOGGER.info("Sync to bigQuery stated");
			String lastSyncedId = findLastSyncedId();
			List<CustomerEntity> customers;
			Integer batchSize = (Integer) ServiceConfigs.consulServiceMap.get("bigQuerySyncBatchSize");
			batchSize = Objects.isNull(batchSize) ? 5000 : batchSize;
			if (Objects.nonNull(lastSyncedId)) {
				customers = customerEntityRepository.findByIdGreaterThan(Integer.valueOf(lastSyncedId),
						PageRequest.of(0, batchSize));
			} else {
				customers = customerEntityRepository.findByIdGreaterThan(0, PageRequest.of(0, batchSize));
			}
			List<Map<String, Object>> customerRecords = customers.parallelStream().map(CustomerEntity::toMap)
					.collect(Collectors.toList());

			List<String> customerIds = customers.parallelStream().map(cust -> cust.getEntityId().toString())
					.collect(Collectors.toList());
			LOGGER.info("Save bigQuery invoked, " + customerIds);
			if (customerIds.isEmpty()) {
				LOGGER.info("Sync to bigQuery skips no records found.");
				return;
			}
			saveRecords(customerRecords);
			LOGGER.info("Complete Sync to bigQuery completed.");
			List<String> notSyncedIds = validateSyncData(customerIds);
			if (notSyncedIds.isEmpty())
				return;
			List<CustomerEntity> syncAgain = customers.stream()
					.filter(cust -> notSyncedIds.contains(cust.getEntityId().toString())).collect(Collectors.toList());
			List<Map<String, Object>> customerRecordsRetry = syncAgain.parallelStream().map(CustomerEntity::toMap)
					.collect(Collectors.toList());
			saveRecords(customerRecordsRetry);
			LOGGER.info("Complete Sync retry to bigQuery completed.");
		} catch (Exception e) {
			LOGGER.error("Error in sync to bigQuery. ", e);
		}
	}

	private String findLastSyncedId() throws CustomerException {
		StringBuilder builder = new StringBuilder();
		builder.append("select MAX(CAST(entityId AS INT64)) from ");
		builder.append(DATA_SET + ".");
		builder.append(TABLE_ID);
		String findLastSyncedId = builder.toString();
		LOGGER.info("Find last syncedId from BigQuery. statement : " + findLastSyncedId);
		QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(findLastSyncedId).setUseLegacySql(false)
				.build();
		try {
			String projectId = bigQuery.getOptions().getProjectId();
			TableResult queryResult = bigQuery.query(queryConfig);
			FieldValueList row = queryResult.getValues().iterator().next();
			String lastSyncId = row.get(0).getValue() == null ? null : row.get(0).getStringValue();
			LOGGER.info("Find last syncedId from BigQuery result. MaxID : " + lastSyncId + " ProjectID: " + projectId);
			return lastSyncId;
		} catch (JobException | InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.error("Error in finding last ID from bigQuery. ", e);
			throw new CustomerException("Error in updating records to BigQuery. Errors : " + e.getMessage());
		}
	}
	
	private List<String> validateSyncData(List<String> ids) throws CustomerException {
		String entityIds = IntStream.range(0, ids.size()).mapToObj(i -> "\'" + ids.get(i) + "\'")
				.collect(Collectors.joining(","));
		StringBuilder builder = new StringBuilder();
		builder.append("select entityId from ");
		builder.append(DATA_SET + ".");
		builder.append(TABLE_ID);
		builder.append(" WHERE entityId IN (" + entityIds + ")");
		String validateSyncQuery = builder.toString();
		LOGGER.info("Validate BigQuery sync. statement : " + validateSyncQuery);
		QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(validateSyncQuery).setUseLegacySql(false)
				.build();
		try {
			TableResult queryResult = bigQuery.query(queryConfig);
			Iterable<FieldValueList> values = queryResult.getValues();
			List<String> insertedIds = new ArrayList<>();
			values.forEach(v -> insertedIds.add(v.get(0).getStringValue()));
			ids.removeAll(insertedIds);
			LOGGER.info("Recors to be retry : " + ids);
			return ids;
		} catch (JobException | InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.error("Error in finding last ID from bigQuery. ", e);
			throw new CustomerException("Error in updating records to BigQuery. Errors : " + e.getMessage());
		}
	}
}
