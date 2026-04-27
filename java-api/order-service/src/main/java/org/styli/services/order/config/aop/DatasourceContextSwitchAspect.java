package org.styli.services.order.config.aop;

import java.util.Objects;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.config.DataSourceContextHolder;
import org.styli.services.order.config.DataSourceEnum;
import org.styli.services.order.utility.Constants;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Datasource Context switch Aspect
 * 
 * @author Chandan Behera
 *
 */
@Aspect
@Component
public class DatasourceContextSwitchAspect {

	private static final Log LOGGER = LogFactory.getLog(DatasourceContextSwitchAspect.class);
	
	@Autowired
	private Gson gson;

	@Autowired
	private DataSourceContextHolder dataSourceContextHolder;
	

	//@Pointcut("@annotation(org.styli.services.order.config.aop.ConfigurableDatasource)")
	@Pointcut("within(org.styli.services.order.controller.*)")
	public void configurableDatasource() {
		// Configurable DataSource PointCut
	}

	@Around("configurableDatasource()")
	public Object datasourceConfigAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
		switchDatasource(joinPoint);
		return joinPoint.proceed();
	}

	private void switchDatasource(ProceedingJoinPoint joinPoint) {
		try {
			String argsString = convertArgumentsToString(joinPoint.getArgs());
			JsonElement useArchive = gson.fromJson(argsString, JsonObject.class).get("useArchive");
			JsonElement orderId = gson.fromJson(argsString, JsonObject.class).get("orderId");
			Integer lastordernumber = Constants.orderCredentials.getArchiveOrderLastNumber();
			if (Objects.nonNull(useArchive) && useArchive.getAsBoolean()) {
				dataSourceContextHolder.setBranchContext(DataSourceEnum.ARCHIVE_DATASOURCE);
			}else if (Objects.nonNull(orderId) && null != lastordernumber
					&& orderId.getAsBigInteger().intValue() < lastordernumber ) {
				dataSourceContextHolder.setBranchContext(DataSourceEnum.ARCHIVE_DATASOURCE);
			}else {
				dataSourceContextHolder.setBranchContext(DataSourceEnum.PRIMARY_DATASOURCE);
			}
		} catch (Exception e) {
			dataSourceContextHolder.setBranchContext(DataSourceEnum.PRIMARY_DATASOURCE);
		}
		//LOGGER.info("Using Context : " + dataSourceContextHolder.getBranchContext());
	}

	private String convertArgumentsToString(Object[] args) {
		StringBuilder buffer = new StringBuilder();
		try {
			if (ArrayUtils.isNotEmpty(args) && args.length >= 2) {
				buffer.append(gson.toJson(args[1]));
			}
		} catch (Exception e) {
			buffer.append(args);
		}catch(StackOverflowError e){
            
			LOGGER.error("stack over flow error");
        }
    
		return buffer.toString();
	}

}
