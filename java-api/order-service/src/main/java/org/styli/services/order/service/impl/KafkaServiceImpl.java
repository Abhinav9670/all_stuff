package org.styli.services.order.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.helper.KafkaBrazeHelper;
import org.styli.services.order.helper.KafkaHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.payFortRefund;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.kafka.BulkWalletUpdateAllString;
import org.styli.services.order.pojo.kafka.DeleteCustomerKafka;
import org.styli.services.order.pojo.order.AddStoreCreditRequest;
import org.styli.services.order.pojo.order.StoreCredit;
import org.styli.services.order.pojo.order.StyliCreditType;
import org.styli.services.order.pojo.response.AddStoreCreditResponse;
import org.styli.services.order.pojo.response.StoreCreditResponse;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.EmailService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.SalesOrderServiceV3;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Response;

@Component
public class KafkaServiceImpl {

    private static final String END_GREET = "Thanks";

	private static final String NEXTLINE = ".\n";

	private static final String INITIATED_AT = " initiated at ";

	private static final String WITH_JOB_ID = "with Job ID: ";

	private static final String BODY_CORE_MSG = "Please find attached the failure report for Styli wallet bulk update request ";

	private static final String INITIAL_GREET = "Hi,\n";

	private static final String WALLET_UPDATE_CSV = "_wallet_update.csv";

	private static final Log LOGGER = LogFactory.getLog(KafkaServiceImpl.class);
    
    private static final String[] HEADERS = { "email_id", "store", "amount_to_be_refunded", "order_no", "comment", "error" };
    
    private static final String JAVA_API = "java-api";

    @Autowired
    SalesOrderServiceV2 salesOrderServiceV2;

    @Autowired
    EmailService emailService;

    @Autowired
    KafkaBrazeHelper kafkaBrazeHelper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.sms.topic}")
    private String kafkaSmsTopic;

    @Value("${customer.wallet.update.topic.internal}")
    private String kafkaWalletTopicInternal;
    
    @Autowired
	private SalesOrderServiceV3 salesOrderServiceV3;

	@Autowired
	private AmastyRmaRequestRepository amastyRmaRequestRepository;

	@Autowired
	private SalesOrderRepository salesOrderRepository;
	
	@Autowired
	private SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;
	
	@Autowired
	private OrderHelper orderHelper;
	
	@Autowired
	private KafkaHelper kafkaHelper;
	
	@Autowired
	private SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	private ObjectMapper mapper;
	
	@Autowired
    ConfigService configService;
	
	@Autowired
	PaymentUtility paymentUtility;

	@Value("${preferred.payment.kafka.topic}")
	private String kafkaPaymentTopic;

    @Value("${customer.wallet.update.braze.topic}")
    private String kafkaWalletTopicBraze;

    @Async("asyncExecutor")
    public void publishSCToKafkaForBraze(Object bulkWalletUpdate) {
        try {
            kafkaTemplate.send(kafkaWalletTopicBraze, bulkWalletUpdate);
        } catch (Exception e) {
            LOGGER.error("Error In Publishing wallet update to Kafka for braze.", e);
        }
    }

    @Async("asyncExecutor")
    public void publishSCToKafka(Object bulkWalletUpdate) {
        try {
            kafkaTemplate.send(kafkaWalletTopicInternal, bulkWalletUpdate);
        } catch (Exception e) {
            LOGGER.error("Error In Publishing wallet update to Kafka.", e);
        }
    }

    @Async("asyncExecutor")
    public void publishToKafka(Object orderId) {
        try {
            kafkaTemplate.send(kafkaSmsTopic, orderId);
        } catch (Exception e) {
            LOGGER.error("Error In Publishing Inventory Qty to Kafka.", e);
        }
    }

	@Async("asyncExecutor")
	public void publishPreferredPaymentToKafka(Object paymentData) {
		try {
			kafkaTemplate.send(kafkaPaymentTopic, paymentData);
		} catch (Exception e) {
			LOGGER.info("Error In Publishing Preferred Payment to Kafka." +e);
		}
	}

	@KafkaListener(topics = "${customer.wallet.update.topic.internal}", groupId = "${kafka.order.service.group}")
	public void receiveRefundMessages(@Payload List<BulkWalletUpdate> messages,
			@Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
			@Header(KafkaHeaders.OFFSET) List<Long> offsets, Acknowledgment acknowledgment) {
		try {
			LOGGER.info("Kafka messages received for bulk wallet update internal : " + messages);
			acknowledgment.acknowledge();
			processKafkaMessages(messages, StyliCreditType.BLANK_ACTION);
		} catch (Exception e) {
			LOGGER.error("Error in processing update wallet kafka message. " + e);
		}
	}

	@KafkaListener(topics = "${customer.wallet.update.topic}", groupId = "${kafka.order.service.group}")
	public void receive(@Payload List<BulkWalletUpdate> messages,
			@Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
			@Header(KafkaHeaders.OFFSET) List<Long> offsets, Acknowledgment acknowledgment) {
		try {
			LOGGER.info("Kafka messages received for bulk wallet update: " + messages.size());
			long startOffset = offsets.get(0);
			long endOffset = offsets.get(offsets.size() - 1);
			acknowledgment.acknowledge();
			if (!Constants.orderCredentials.isWalletUpdateProcessFlag()) {
				LOGGER.info("Skipping kafka wallet messages on startOffset: " + startOffset + " and  endOffset: " + endOffset);
			} else {
				LOGGER.info(
						"Processing kafka messages for wallet on startOffset: " + startOffset + " and endOffset : " + endOffset);
				processKafkaMessages(messages, StyliCreditType.FINANCE_BULK_CHANGES);
			}
		} catch (Exception e) {
			LOGGER.error("Error in updating wallet : " + e);
		}

	}

	@KafkaListener(topics = "${customer.wallet.update.braze.topic}", groupId = "${kafka.order.service.group}")
	public void receiveBrazeUpdates(@Payload List<BulkWalletUpdate> messages,
			@Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
			@Header(KafkaHeaders.OFFSET) List<Long> offsets, Acknowledgment acknowledgment) {
		try {

			LOGGER.info("Kafka messages received for wallet update from braze: " + messages.size());
			long startOffset = offsets.get(0);
			long endOffset = offsets.get(offsets.size() - 1);
			acknowledgment.acknowledge();
			LOGGER.info("Processing kafka messages for braze on startOffset: " + startOffset + " and endOffset is: "
					+ endOffset);

			processKafkaMessages(messages, StyliCreditType.BRAZE_UPDATE);

		} catch (Exception e) {
			LOGGER.error("Global catch for kafka from braze");
			LOGGER.error(e.getMessage());
		}
	}

	private void processKafkaMessages(List<BulkWalletUpdate> messages, StyliCreditType actionType) {
		String directoryName = "wallet_update";
		File directory = new File(directoryName);
		if (!directory.exists()) {
			directory.mkdir();
		}

		boolean sendEmail = false;
		String toEmail = "satyaprakash.dash@landmarkgroup.com";
		List<BulkWalletUpdateAllString> errorsList = new ArrayList<>();

		AddStoreCreditRequest addStoreCreditRequest = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();

		for (int i = 0; i < messages.size(); i++) {
			try {
				BulkWalletUpdate message = mapper.readValue(String.valueOf(messages.get(i)), BulkWalletUpdate.class);
				StoreCredit storeCredit = new StoreCredit();
				storeCredit.setEmailId(message.getEmail());
				storeCredit.setCustomerId(message.getCustomerId());
				storeCredit.setStoreId(message.getStore_id());
				storeCredit.setStoreCredit(message.getAmount_to_be_refunded());
				storeCredit.setComment(message.getComment());
				storeCredit.setReferenceNumber(message.getOrder_no());
				storeCredit.setStore(message.getStore());
				storeCredit.setInitiatedBy(message.getInitiatedBy());
				storeCredit.setInitiatedTime(message.getInitiatedTime());
				storeCredit.setJobId(message.getJobId());
				storeCredit.setReturnableToBank(message.isReturnableToBank());
				storeCredits.add(storeCredit);

				if (!message.getInitiatedBy().equals(JAVA_API)) {
					if (message.getCounter() != null && message.getTotalCount() != null
							&& message.getCounter().equals(message.getTotalCount())) {
						sendEmail = true;
					}
					if (StringUtils.isNotBlank(message.getInitiatedBy()))
						toEmail = message.getInitiatedBy();
				}

			} catch (JsonProcessingException e) {
				LOGGER.error("error parsing : " + e);
				try {
					BulkWalletUpdateAllString ms = mapper.readValue(String.valueOf(messages.get(i)),
							BulkWalletUpdateAllString.class);
					ms.setExceptionMessage(e.getMessage());
					errorsList.add(ms);
					if (!ms.getInitiatedBy().equals(JAVA_API)) {
						if (Objects.nonNull(ms.getCounter()) && Objects.nonNull(ms.getTotalCount())
								&& ms.getCounter().equals(ms.getTotalCount())) {
							sendEmail = true;
						}
						if (StringUtils.isNotBlank(ms.getInitiatedBy()))
							toEmail = ms.getInitiatedBy();
					}
				} catch (JsonProcessingException ex) {
					LOGGER.error("error parsing allString : " + e);
				}
			}
		}
		String jobId = null;
		Date date = null;
		if (CollectionUtils.isNotEmpty(errorsList) && actionType.equals(StyliCreditType.BRAZE_UPDATE)) {
			kafkaBrazeHelper.sendErrorsListToBraze(errorsList);
		}
		if (CollectionUtils.isNotEmpty(errorsList)) {
			LOGGER.error("Send Error Email");
			String fileName = getFileForJsonProcessingErrors(directoryName, errorsList);

			for (BulkWalletUpdateAllString str : errorsList) {
				if (!str.getInitiatedBy().equals(JAVA_API)) {
					if (str.getInitiatedTime() != null)
						date = new Date(Long.parseLong(str.getInitiatedTime()) * 1000);
					jobId = str.getJobId();
					break;
				}
			}
			String subject = "Failure in Styli wallet bulk update job (JSON Parsing) JOB-ID: " + jobId;
			String body = INITIAL_GREET + BODY_CORE_MSG
					+ WITH_JOB_ID + jobId + INITIATED_AT + date + NEXTLINE + END_GREET;
			Response response = emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
			if (ObjectUtils.isEmpty(response)) {
				LOGGER.info("Re-trying Error email");
				emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
			}
		}

		if (CollectionUtils.isNotEmpty(storeCredits)) {

			for (StoreCredit sc : storeCredits) {
				if (!sc.getInitiatedBy().equals(JAVA_API)) {
					if (sc.getInitiatedTime() != null)
						date = new Date(Long.parseLong(sc.getInitiatedTime()) * 1000);
					jobId = sc.getJobId();
					break;
				}
			}

			try {
				addStoreCreditRequest.setStoreCredits(storeCredits);
				addStoreCreditRequest.setStyliCreditType(actionType);
				addStoreCreditRequest.setUpdateRequestType("bulk");
				AddStoreCreditResponse resp = salesOrderServiceV2.addStoreCredit(addStoreCreditRequest);
				List<StoreCreditResponse> failedRows = resp.getResponse().stream().filter(e -> !e.getStatus())
						.collect(Collectors.toList());
				List<StoreCreditResponse> successRows = resp.getResponse().stream()
						.filter(StoreCreditResponse::getStatus).collect(Collectors.toList());

				if (actionType.equals(StyliCreditType.BRAZE_UPDATE)) {
					kafkaBrazeHelper.sendResultToBraze(successRows, failedRows);
				}

				if (CollectionUtils.isNotEmpty(failedRows)) {
					String fileName = getFileNameForJavaErrors(directoryName, failedRows);
					String subject = "Failure in Styli wallet bulk update job (JAVA Error) JOB-ID: " + jobId;
					String body = INITIAL_GREET
							+ BODY_CORE_MSG
							+ WITH_JOB_ID + jobId + INITIATED_AT + date + NEXTLINE + END_GREET;
					LOGGER.info("Wallet update error email : " + body);
					Response response = emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
					if (ObjectUtils.isEmpty(response)) {
						LOGGER.info("Re-trying JAVA Error email");
						emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
					}
				}
				LOGGER.info("All batch messages received");
			} catch (Exception e) {

				if (actionType.equals(StyliCreditType.BRAZE_UPDATE)) {
					kafkaBrazeHelper.sendExceptionListToBraze(storeCredits);
				}

				String fileName = getFileNameForServiceExceptions(directoryName, storeCredits, e);
				String subject = "Failure in Styli wallet bulk update job (Service Error) JOB-ID: " + jobId;
				String body = INITIAL_GREET + BODY_CORE_MSG
						+ WITH_JOB_ID + jobId + INITIATED_AT + date + NEXTLINE + END_GREET;
				LOGGER.info("Wallet update error email : " + body);
				Response response = emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
				if (ObjectUtils.isEmpty(response)) {
					LOGGER.info("Re-trying Error email");
					emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
				}

			}
		}

		if (sendEmail) {
			String subject = "Styli wallet bulk update - Job ID: " + jobId + " completed";
			String body = INITIAL_GREET + "The request for updating the Styli wallet with job ID: " + jobId + INITIATED_AT
					+ date + " has been completed at " + new Date() + NEXTLINE
					+ "Please check your email if any failures happened while updating the wallet. Also, please note there could be multiple emails for the failures.\n"
					+ END_GREET;
			LOGGER.info("Wallet update email : " + body);
			Response response = emailService.sendText(toEmail, subject, body);
			if (response == null) {
				LOGGER.info("Re-trying success email");
				emailService.sendText(toEmail, subject, body);
			}
		}
	}

    @NotNull
	private String getFileNameForServiceExceptions(String directoryName, List<StoreCredit> storeCredits, Exception e) {
		String fileName = null;
		String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown Error";

		fileName = directoryName + "/" + new Date().getTime() + WALLET_UPDATE_CSV;
		try (FileWriter out = new FileWriter(fileName)) {
			CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS));
			storeCredits.stream().filter(el -> !el.getInitiatedBy().equals(JAVA_API)).forEach(sc -> {
				try {
					printer.printRecord(sc.getEmailId(), sc.getStore(), sc.getStoreCredit(), sc.getReferenceNumber(),
							sc.getComment(), errorMessage);
				} catch (IOException ex) {
					LOGGER.error("Error printing to file : " + ex);
				}
			});
			out.flush();
			printer.close();
		} catch (Exception ex) {
			LOGGER.error("error printing to file : " + ex);
		}

		return fileName;
	}

    @NotNull
	private String getFileNameForJavaErrors(String directoryName, List<StoreCreditResponse> resp) {
		String fileName = null;
		fileName = directoryName + "/" + new Date().getTime() + WALLET_UPDATE_CSV;
		try (FileWriter out = new FileWriter(fileName)) {
			CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS));
			resp.forEach(e -> {
				StoreCredit sc = e.getActualRequest();
				if (!sc.getInitiatedBy().equals(JAVA_API)) {
					try {
						printer.printRecord(sc.getEmailId(), sc.getStore(), sc.getStoreCredit(),
								sc.getReferenceNumber(), sc.getComment(), e.getStatusMsg());
					} catch (IOException ex) {
						LOGGER.error("error printing to file");
						LOGGER.error(ex.getMessage());
					}
				}
			});
			out.flush();
			printer.close();
		} catch (IOException ex) {
			LOGGER.error("error printing to file : " + ex);
		}
		return fileName;
	}

    @NotNull
	private String getFileForJsonProcessingErrors(String directoryName, List<BulkWalletUpdateAllString> errorsList) {
		String fileName = null;
		fileName = directoryName + "/" + new Date().getTime() + WALLET_UPDATE_CSV;
		try (FileWriter out = new FileWriter(fileName)) {
			CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS));
			errorsList.stream().filter(e -> !e.getInitiatedBy().equals(JAVA_API)).forEach(e -> {
				try {
					printer.printRecord(e.getEmail(), e.getStore(), e.getAmount_to_be_refunded(), e.getOrder_no(),
							e.getComment(), e.getExceptionMessage());
				} catch (IOException ex) {
					LOGGER.error("error printing to file");
					LOGGER.error(ex.getMessage());
				}
			});
			out.flush();
			printer.close();
		} catch (IOException e) {
			LOGGER.error("file read exception! : " + e);
		}
		return fileName;
	}
    
    @KafkaListener(topics = "${queue.based.refund.dropoff}", groupId = "${kafka.order.service.group}")
    @Transactional
	public void receiveRefundDropoff(@Payload List<String> messages,
			@Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
			@Header(KafkaHeaders.OFFSET) List<Long> offsets, Acknowledgment acknowledgment) {

		try{
			LOGGER.info("Kafka messages received for refund dropoff : " + messages);
			long startOffset = offsets.get(0);
			long endOffset= offsets.get(offsets.size() - 1);

			acknowledgment.acknowledge();

			if(!Constants.orderCredentials.isWalletUpdateProcessFlag()) {
				LOGGER.info("Skipping kafka messages on startOffset: " + startOffset + " and endOffset is : " + endOffset);
			} else{
				LOGGER.info(
						"Processing kafka messages on startOffset: " + startOffset + " and endOffset: " + endOffset);

				messages.stream().filter(Objects::nonNull).forEach(msg -> {
					try {
						LOGGER.info("Return Refund initiated with message : " + msg);
						payFortRefund refundMsg = mapper.readValue(msg, payFortRefund.class);
						processKafka(refundMsg);
					} catch (Exception e) {
						LOGGER.error("Error in processing Return Refund. Message : " + msg +  " Error: " + e);
					}
				});
			}
		} catch(Exception e) {
			LOGGER.error("Global catch for kafka. Error " + e);
		}

	}

    @Transactional
	public void processKafka(payFortRefund messages) {
		RefundPaymentRespone refundResp= processKafkaMessages(messages);
		if(refundResp.isStatus()) {
			AmastyRmaRequest rmaRequest = amastyRmaRequestRepository
					.findByRmaIncIdAndOrderId(messages.getReturnIncrementId(), messages.getOrderId());
			SalesOrder order= salesOrderRepository.findByEntityId(messages.getOrderId());
			if(Objects.nonNull(order) ) {
				Hibernate.initialize(order.getSalesOrderItem());
				LOGGER.info("Update Order and send sms through kafka Return Refund of OrderId: " + order.getEntityId());
				BigDecimal sumOrderedQty= order.getSalesOrderItem().stream()
						.filter(e-> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.map(SalesOrderItem::getQtyOrdered).reduce(BigDecimal.ZERO, BigDecimal::add);

				BigDecimal sumOrderedRefunded= order.getSalesOrderItem().stream()
						.filter(e-> !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
						.map(SalesOrderItem::getQtyRefunded).reduce(BigDecimal.ZERO, BigDecimal::add);
				String msg= null;
				if(rmaRequest.getReturnType() == 0) {
					msg= rmaRequest.getRmaIncId() + " "+ "picked up";
				} else{
					msg= rmaRequest.getRmaIncId() + " "+ "dropped off";
				}
				if (sumOrderedQty.compareTo(sumOrderedRefunded) == 0) {
					LOGGER.info("ordered and returned qty are same " + order.getEntityId());
					order.setStatus(OrderConstants.REFUNDED_ORDER_STATUS);
					salesOrderRepository.saveAndFlush(order);
					updateOrderStatusHistory(order, msg, "rma", OrderConstants.REFUNDED_ORDER_STATUS);
					saveOrderGrid(order, OrderConstants.REFUNDED_ORDER_STATUS);
				} else{
					LOGGER.info("ordered and returned qty are not same " + order.getEntityId());
					SalesOrderStatusHistory salesOrderStatusHistory = salesOrderStatusHistoryRepository
							.findByParentIdOrderByEntityIdDesc(order.getEntityId());
					salesOrderStatusHistory.setComment("Order updated with message: " + msg);
					salesOrderStatusHistoryRepository.saveAndFlush(salesOrderStatusHistory);
				}
				SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
				if (Objects.nonNull(orderPayment)) {
					processSmsAndEmail(refundResp, rmaRequest, orderPayment.getMethod());
				}
			}
		}
	}

	private void processSmsAndEmail(RefundPaymentRespone refundResp, AmastyRmaRequest rmaRequest,
			String paymentMethod) {
		switch (paymentMethod) {
		case OrderConstants.PAYMENT_METHOD_COD:
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_COD_FREE, refundResp.getRefundAmount(),null);
			break;
		case "apple_pay":
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_PREPAID_CARD, refundResp.getRefundAmount(),null);
			break;
		case "md_payfort_cc_vault":
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_PREPAID_CARD, refundResp.getRefundAmount(),null);
			break;
		case "md_payfort":
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_PREPAID_CARD, refundResp.getRefundAmount(),null);
			break;
		case OrderConstants.PAYMENT_METHOD_TYPE_FREE:
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_COD_FREE, refundResp.getRefundAmount(),null);
			break;
		case "tabby_installments":
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_TABBY, refundResp.getRefundAmount(),null);
			break;
		case "tabby_paylater":
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_TABBY_PAYLATER, refundResp.getRefundAmount(),null);
			break;
		case "tamara_installments_3":
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_TABBY, refundResp.getRefundAmount(),null);
			break;
		case "tamara_installments_6":
			orderHelper.sendSmsAndEMail(rmaRequest.getRmaIncId(), OrderConstants.REFUND_TYPE_RETURN,
					OrderConstants.SMS_REFUND_TEMPLATE_TABBY, refundResp.getRefundAmount(),null);
			break;
		default:
			LOGGER.info("Invalid Payment Method");
			break;
		}
	}

	public void updateOrderStatusHistory(SalesOrder order, String msg, String entity, String status) {
		SalesOrderStatusHistory sh= new SalesOrderStatusHistory();
		LOGGER.info("Update Order status history table through kafka return refund ");
		sh.setParentId(order.getEntityId());
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(0);
		sh.setComment("Order updated with message: " + msg);
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setStatus(status);
		sh.setEntityName(entity);
		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}

	/**
	* @paramorder
	* @parammessage
	*/
	public void saveOrderGrid(SalesOrder order, String message) {
		SalesOrderGrid salesorderGrid= salesOrderGridRepository.findByEntityId(order.getEntityId());
		LOGGER.info("Update saveOrderGrid table through kafka Return Refund of OrderId: " + order.getEntityId());
		salesorderGrid.setStatus(message);
		salesOrderGridRepository.saveAndFlush(salesorderGrid);
	}

	private RefundPaymentRespone processKafkaMessages(payFortRefund messages) {

		LOGGER.info("Processing kafka messages on Refund of OrderId: " + messages.getOrderId());

		return salesOrderServiceV3.payfortRefundCall(null, messages);

	}
	
	@KafkaListener(topics = "${queue.based.delete.customer}", groupId = "${kafka.order.service.group}")
    @Transactional
	public void deleteCustomerInfoFromOrders(@Payload List<String> messages,
			@Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
			@Header(KafkaHeaders.OFFSET) List<Long> offsets, Acknowledgment acknowledgment) {

		try{
			LOGGER.info("Kafka messages received for delete customer : " + messages);
			long startOffset = offsets.get(0);
			long endOffset= offsets.get(offsets.size() - 1);

			acknowledgment.acknowledge();
			
			LOGGER.info(
					"Processing kafka messages on startOffset: " + startOffset + " and endOffset: " + endOffset);

			messages.stream().filter(Objects::nonNull).forEach(msg -> {
				try {
					LOGGER.info("Delete Customer initiated with message : " + msg);
 					DeleteCustomerKafka deleteCustomerKafka = mapper.readValue(msg, DeleteCustomerKafka.class);
 					kafkaHelper.processKafkaDeleteCustomer(deleteCustomerKafka);
				} catch (Exception e) {
					LOGGER.error("Error in processing Delete Customer. Message : " + msg +  " Error: " + e);
				}
			});
			
		} catch(Exception e) {
			LOGGER.error("Global catch for kafka. Error " + e);
		}

	}

}
