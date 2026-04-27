package org.styli.services.order.repository.Eav;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.Eav.EavAttribute;

@Repository
public interface EavAttributeRepository extends JpaRepository<EavAttribute, Integer> {

    // @Query(name = "findAllProductListMapping",nativeQuery = true)
    // List<ProductDetailDTO> getProducts();
    //
    @Query(value = "SELECT eav FROM EavAttribute eav where eav.entityTypeId in (1,2,3,4)")
    // other properties
    List<EavAttribute> getEavAttributes();

    EavAttribute findByAttributeId(Integer superAttributeId);

}