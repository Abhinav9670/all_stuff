package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class PreferredPaymentData {

        private String paymentMethod;

        private Integer customerId;

        private Integer storeId;

        public PreferredPaymentData(String paymentMethod, Integer customerId, Integer storeId) {
        }
}



