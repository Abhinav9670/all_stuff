package org.styli.services.order.logging.config;

import com.google.common.collect.ImmutableList;

public class LoggerConstants {

	
	 public static final ImmutableList<String> REMOVE_URI_FROM_LOG_LIST = 
   		  ImmutableList.of("/inventory/v2/status", 
   				  "/getstoresarray"
   				  ,"/getallstoregroup",
   				  "list/monitor/525");
	 
	 public static final String REQUEST_BODY_ATTRIBUTE = "REQUEST_BODY";
}
