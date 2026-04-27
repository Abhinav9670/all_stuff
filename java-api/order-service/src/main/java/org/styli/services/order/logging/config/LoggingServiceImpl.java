package org.styli.services.order.logging.config;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
// @Log
public class LoggingServiceImpl implements LoggingService {

    private static final Log LOGGER = LogFactory.getLog(LoggingServiceImpl.class);
    
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void logRequest(HttpServletRequest httpServletRequest, Object body) {
        StringBuilder stringBuilder = new StringBuilder();
        Map<String, String> parameters = buildParametersMap(httpServletRequest);

        stringBuilder.append("\n");
        stringBuilder.append("REQUEST:");
        stringBuilder.append("\n");
        stringBuilder.append("method=[").append(httpServletRequest.getMethod()).append("] ");
        stringBuilder.append("\n");
        stringBuilder.append("path=[").append(httpServletRequest.getRequestURI()).append("] ");
        stringBuilder.append("\n");
        stringBuilder.append("headers=").append(buildJsonHeadersMap(httpServletRequest));

        if (!parameters.isEmpty()) {
            stringBuilder.append("\n");
            stringBuilder.append("parameters=[").append(parameters).append("] ");
        }

        if (body != null) {
            stringBuilder.append("\n");
            String jsonBodyString = null;
            try {
                jsonBodyString = mapper.writeValueAsString(body);
            } catch (JsonProcessingException e) {
                LOGGER.error("Error in parsing " + e.getMessage());
            }
            stringBuilder.append("Request Body=" + jsonBodyString);
        }

        LOGGER.info(stringBuilder.toString());
    }

    @Override
    public void logResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
            Object body) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("RESPONSE:");
        stringBuilder.append("\n");
        stringBuilder.append("method=[").append(httpServletRequest.getMethod()).append("] ");
        stringBuilder.append("\n");
        stringBuilder.append("path=[").append(httpServletRequest.getRequestURI()).append("] ");
        stringBuilder.append("\n");
        stringBuilder.append("responseHeaders=").append(buildJsonHeadersMap(httpServletResponse));
        stringBuilder.append("Custom Headers=[").append(customBuildHeadersMap(httpServletRequest)).append("] ");
        stringBuilder.append("\n");

        // Include request body in response log
        Object requestBody = httpServletRequest.getAttribute(LoggerConstants.REQUEST_BODY_ATTRIBUTE);
        if (requestBody != null) {
            String jsonRequestBodyString = null;
            try {
                jsonRequestBodyString = mapper.writeValueAsString(requestBody);
            } catch (JsonProcessingException e) {
                LOGGER.error("Error in parsing request body for response log: ", e);
            }
            if (jsonRequestBodyString != null) {
                stringBuilder.append("Request Body=").append(jsonRequestBodyString);
                stringBuilder.append("\n");
            }
        }

        String jsonResponseString = null;
        try {
            jsonResponseString = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {

        	LOGGER.error("Error in parsing response log" + e.getMessage());
        }
        stringBuilder.append("Response Body=").append(jsonResponseString);
        if(!LoggerConstants.REMOVE_URI_FROM_LOG_LIST.contains(httpServletRequest.getRequestURI())) {
        	
        	LOGGER.info(stringBuilder.toString());
        }
        
    }

    private Map<String, String> buildParametersMap(HttpServletRequest httpServletRequest) {
        Map<String, String> resultMap = new HashMap<>();
        Enumeration<String> parameterNames = httpServletRequest.getParameterNames();

        while (parameterNames.hasMoreElements()) {
            String key = parameterNames.nextElement();
            String value = httpServletRequest.getParameter(key);
            resultMap.put(key, value);
        }

        return resultMap;
    }

    private Map<String, String> customBuildHeadersMap(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            String value = request.getHeader(key);
            if (null != key && key.equalsIgnoreCase("x-Source")
                    || null != key && key.equalsIgnoreCase("X-Client-Version")
                    || null != key && key.equalsIgnoreCase("Screen-Name")
                    || null != key && key.equalsIgnoreCase("Device-Id")
                    || null != key && key.equalsIgnoreCase("Token")
                    || null != key && key.equalsIgnoreCase("x-header-token")) {
                map.put(key, value);
            }

        }

        return map;
    }

    private String buildJsonHeadersMap(HttpServletResponse response) {
        Map<String, String> map = new HashMap<>();

        Collection<String> headerNames = response.getHeaderNames();
        for (String header : headerNames) {
            map.put(header, response.getHeader(header));
        }

        try {
			return mapper.writeValueAsString(map);
		} catch (JsonProcessingException e) {
			LOGGER.error("exception occoured during convert request header conversion to json string:"+e.getMessage());
		}
        
		return null;
    }
    
    private String buildJsonHeadersMap(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            String value = request.getHeader(key);
            map.put(key, value);
        }

        try {
			return mapper.writeValueAsString(map);
		} catch (JsonProcessingException e) {
			LOGGER.error("exception occoured during convert request header conversion to json string:"+e.getMessage());
		}
        
		return null;
    }
}
