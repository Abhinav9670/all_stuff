package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesOrderGrid;

import java.util.List;

public interface SalesOrderGridRepository
		extends JpaRepository<SalesOrderGrid, Integer>, JpaSpecificationExecutor<SalesOrderGrid> {

	SalesOrderGrid findByEntityId(Integer entityId);

	List<SalesOrderGrid> findByCustomerEmail(String email);

	List<SalesOrderGrid> findByCustomerId(Integer customerId);

	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetails(
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);

	@Query(value = "SELECT * FROM sales_order_grid "
			+ "WHERE customer_name LIKE %?1% "
			+ "AND payment_method = ?2 "
			+ "ORDER BY created_at DESC "
			+ "LIMIT ?3 OFFSET ?4", nativeQuery = true)
	List<SalesOrderGrid> findOmsOrderDetailsList(String customerName, String paymentMethod, int limit, int offset);
	@Query(value = "SELECT COUNT(*) FROM sales_order_grid WHERE customer_name LIKE %?1% AND payment_method = ?2", nativeQuery = true)
	long countOmsOrderDetailsList(String customerName, String paymentMethod);

	@Query(value = "SELECT * FROM sales_order_grid "
			+ "WHERE customer_name LIKE %?1% "
			+ "ORDER BY created_at DESC "
			+ "LIMIT ?2 OFFSET ?3", nativeQuery = true)
	List<SalesOrderGrid> findOmsOrderDetailsListByCustomer(String customerName, int limit, int offset);

	@Query(value = "SELECT COUNT(*) FROM sales_order_grid WHERE customer_name LIKE %?1%", nativeQuery = true)
	long countOmsOrderDetailsListByCustomer(String customerName);

	@Query(value = "SELECT * FROM sales_order_grid "
			+ "WHERE payment_method=?1 "
			+ "ORDER BY created_at DESC "
			+ "LIMIT ?2 OFFSET ?3", nativeQuery = true)
	List<SalesOrderGrid> findOmsOrderDetailsListByPayment(String paymentMethod, int limit, int offset);

	@Query(value = "SELECT COUNT(*) FROM sales_order_grid WHERE payment_method=?1", nativeQuery = true)
	long countOmsOrderDetailsListByPayment(String paymentMethod);

	@Query(value = "SELECT * FROM sales_order_grid ORDER BY created_at DESC "
			+ "LIMIT ?1 OFFSET ?2", nativeQuery = true)
	List<SalesOrderGrid> findOmsOrderDetailsListAll(int limit, int offset);

	@Query(value = "SELECT COUNT(*) FROM sales_order_grid", nativeQuery = true)
	long countOmsOrderDetailsListAll();

	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND status IN (:status) " +
					"AND app_version IN (:appVersion) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND status IN (:status) " +
					"AND app_version IN (:appVersion) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForAll(
			@Param("storeId") List<Integer> storeId,
			@Param("source") List<Integer> source,
			@Param("status") List<String> status,
			@Param("appVersion") List<String> appVersion,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreId(
			@Param("storeId") List<Integer> storeId,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);



	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForSource(
			@Param("source") List<Integer> source,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);



	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStatus(
			@Param("status") List<String> status,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE app_version IN (:appVersion) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE app_version IN (:appVersion) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForAppVersion(
			@Param("appVersion") List<String> appVersion,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);



	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDates(
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND status IN (:status) " +
					"AND app_version IN (:appVersion) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND status IN (:status) " +
					"AND app_version IN (:appVersion) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForAll(
			@Param("storeId") List<Integer> storeId,
			@Param("source") List<Integer> source,
			@Param("status") List<String> status,
			@Param("appVersion") List<String> appVersion,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStatus(
			@Param("status") List<String> status,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStoreId(
			@Param("storeId") List<Integer> storeId,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForMailId(
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR creatxed_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForSource(
			@Param("source") List<Integer> source,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE increment_id IN (:incrementId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE increment_id IN (:incrementId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForIncrementId(
			@Param("incrementId") List<String> incrementIdList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStoreStatusSourceAndMail(
			@Param("storeId") List<Integer> storeId,
			@Param("status") List<String> status,
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStoreAndMail(
			@Param("storeId") List<Integer> storeId,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStatusAndMail(
			@Param("status") List<String> status,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForSourceAndMail(
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForSourceStoreIdAndStatus(
			@Param("source") List<Integer> source,
			@Param("storeId") List<Integer> storeId,
			@Param("status") List<String> status,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStatusSourceAndMail(
			@Param("status") List<String> status,
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageable);



	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStoreStatusAndMail(
			@Param("storeId") List<Integer> storeId,
			@Param("status") List<String> status,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStoreSourceAndMail(
			@Param("storeId") List<Integer> storeId,
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStatusAndSource(
			@Param("status") List<String> status,
			@Param("source") List<Integer> source,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStoreAndSource(
			@Param("storeId") List<Integer> storeId,
			@Param("source") List<Integer> source,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (created_at >= :fromDate OR created_at IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (created_at <= :toDate OR created_at IS NULL) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsByDatesForStoreAndStatus(
			@Param("storeId") List<Integer> storeId,
			@Param("status") List<String> status,
			@Param("customerName") String customerName,
			@Param("fromDate") String fromDate,
			@Param("toDate") String toDate,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE (customer_email IN (:queryString) OR increment_id IN (:queryString)) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE (customer_email IN (:queryString) OR increment_id IN (:queryString)) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName% OR customer_name IS NULL) " +
					"AND (:paymentMethod = '' OR payment_method LIKE %:paymentMethod%) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderQueryDetails(
			@Param("queryString") List<String> queryList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page);


	Page<SalesOrderGrid> findByCustomerNameContaining(
			String customerName, Pageable pageable);

	Page<SalesOrderGrid> findByCustomerEmailLike(
			String customerEmail, Pageable pageable);

	Page<SalesOrderGrid> findByIncrementIdContaining(
			String incrementId, Pageable pageable);

	@Query(value = "select * from sales_order_grid so where so.customer_id is null "
			+ "and so.created_at BETWEEN (NOW() - INTERVAL :hoursAgo HOUR) AND (NOW())", nativeQuery = true)
	List<SalesOrderGrid> getAllOrdersForCustIdUpdate(@Param("hoursAgo") Integer hoursAgo);

	@Query(
			value = "SELECT * FROM sales_order_grid WHERE customer_name LIKE %:customerName% and (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid WHERE customer_name LIKE %:customerName% and (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderQueryDetailsForArabic(
			@Param("customerName") String customerName,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable pageableSizeSortCreatedAtDesc
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",

			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForMailList(
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE (customer_email IN (:customerEmail) OR customer_id IN (:customerId)) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE (customer_email IN (:customerEmail) OR customer_id IN (:customerId)) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForMailListCustomerId(
			@Param("customerEmail") List<String> emailList,
			@Param("customerId") String customerId,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE increment_id IN (:incrementId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE increment_id IN (:incrementId) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForIncrementIdList(
			@Param("incrementId") List<String> incrementIdList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);



	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreIdAndStatus(
			@Param("storeId") List<Integer> storeIds,
			@Param("status") List<String> status,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreIdAndStatusAndSource(
			@Param("storeId") List<Integer> storeIds,
			@Param("status") List<String> status,
			@Param("source") List<Integer> source,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);

	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStatusAndSource(
			@Param("status") List<String> status,
			@Param("source") List<Integer> source,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);

	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreAndSource(
			@Param("storeId") List<Integer> storeIds,
			@Param("source") List<Integer> source,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);

	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreIdAndStatusAndSourceAndMail(
			@Param("storeId") List<Integer> storeIds,
			@Param("status") List<String> status,
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);

	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreIdAndMail(
			@Param("storeId") List<Integer> storeIds,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForSourceAndMail(
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStatusAndMail(
			@Param("status") List<String> status,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND status IN (:status) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreIdAndStatusAndMail(
			@Param("storeId") List<Integer> storeIds,
			@Param("status") List<String> status,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);



	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE store_id IN (:storeId) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStoreIdAndSourceAndMail(
			@Param("storeId") List<Integer> storeIds,
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(
			value = "SELECT * FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			countQuery = "SELECT COUNT(*) FROM sales_order_grid " +
					"WHERE status IN (:status) " +
					"AND source IN (:source) " +
					"AND customer_email IN (:customerEmail) " +
					"AND (:customerName = '' OR customer_name LIKE %:customerName%) " +
					"AND (:paymentMethod = '' OR payment_method = :paymentMethod) " +
					"AND (is_split_order IN(:isSplitOrder) OR (:includeNullSplitOrder = true AND is_split_order IS NULL))",
			nativeQuery = true
	)
	Page<SalesOrderGrid> findOmsOrderDetailsForStatusAndSourceAndMail(
			@Param("status") List<String> status,
			@Param("source") List<Integer> source,
			@Param("customerEmail") List<String> emailList,
			@Param("customerName") String customerName,
			@Param("paymentMethod") String paymentMethod,
			@Param("isSplitOrder") List<Integer> isSplitOrder,
			@Param("includeNullSplitOrder") boolean includeNullSplitOrder,
			Pageable page
	);


	@Query(value = "select * from sales_order_grid so,  sales_order_address soa WHERE soa.parent_id = so.entity_id and  soa.telephone = ?1 and soa.address_type='shipping'"
			+ " and (so.customer_id is null OR so.customer_id = 0)", nativeQuery = true)
	List<SalesOrderGrid> findGuestOrdersByTelephone(String telephone);

	List<SalesOrderGrid> findByIncrementId(String incrementId);

}