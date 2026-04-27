package org.styli.services.customer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.customer.model.Store;

public interface StoreRepository extends JpaRepository<Store, Integer>, JpaSpecificationExecutor<Store> {

    Store findByStoreId(Integer storeId);

    List<Store> findByWebSiteId(Integer webSiteId);
}