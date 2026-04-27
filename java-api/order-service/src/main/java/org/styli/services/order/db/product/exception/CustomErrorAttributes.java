//package org.styli.services.order.db.product.exception;
//
//import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
//import org.springframework.stereotype.Component;
//import org.springframework.web.context.request.WebRequest;
//
//import java.util.Map;
//
///**
// * @author umesh.mahato@landmarkgroup.com
// * @project order-service
// * @created 13/05/2022 - 1:06 PM
// */
//
//@Component
//public class CustomErrorAttributes extends DefaultErrorAttributes {
//
//    @Override
//    public Map<String, Object> getErrorAttributes(WebRequest webRequest, boolean includeStackTrace) {
//
//        Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, includeStackTrace);
//        errorAttributes.remove("message");
////        errorAttributes.put("version", "1.2");
//        return errorAttributes;
//
//    }
//
//}
