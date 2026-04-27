package org.styli.services.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.repository.query.Param;
import org.styli.services.customer.model.CoreConfigData;

public interface CoreConfigDataRepository
        extends JpaRepository<CoreConfigData, Integer>, JpaSpecificationExecutor<CoreConfigData> {

    CoreConfigData findByPathAndScopeId(@Param("path") String path, @Param("scopeId") Integer scopeId);

}