package org.styli.services.order.config.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation takes care of switch to archive database based on the requirement.
 * 
 * Uses: Annotate required endpoint method and make sure to pass useArchive=true in request body. 
 * @author chandanbehera
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConfigurableDatasource {
}