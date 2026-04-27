package org.styli.services.order.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Datasource config
 * @author chandanbehera
 *
 */
@Component
public class DataSourceRouting extends AbstractRoutingDataSource {
	private PrimaryDataSourceConfig primaryDataSource;
	private ArchiveDataSourceConfig archiveDataSource;
	private DataSourceContextHolder dataSourceContextHolder;

	public DataSourceRouting(DataSourceContextHolder dataSourceContextHolder, PrimaryDataSourceConfig primaryDataSource,
			ArchiveDataSourceConfig archiveDataSource) {
		this.archiveDataSource = archiveDataSource;
		this.primaryDataSource = primaryDataSource;
		this.dataSourceContextHolder = dataSourceContextHolder;

		Map<Object, Object> dataSourceMap = new HashMap<>();
		dataSourceMap.put(DataSourceEnum.PRIMARY_DATASOURCE, primaryDatasource());
		dataSourceMap.put(DataSourceEnum.ARCHIVE_DATASOURCE, archiveDatasource());
		this.setTargetDataSources(dataSourceMap);
		this.setDefaultTargetDataSource(primaryDatasource());
	}

	@Override
	protected Object determineCurrentLookupKey() {
		return dataSourceContextHolder.getBranchContext();
	}

	public DataSource primaryDatasource() {
		final HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(primaryDataSource.getUrl());
		ds.setUsername(primaryDataSource.getUsername());
		ds.setPassword(primaryDataSource.getPassword());
		
		ds.setMaximumPoolSize(primaryDataSource.getMaximumPoolSize());
		ds.setMinimumIdle(primaryDataSource.getMinimumIdle());
		ds.setConnectionTimeout(primaryDataSource.getConnectionTimeout());
		ds.setIdleTimeout(primaryDataSource.getIdleTimeout());
		return ds;
	}

	@Bean(name ="archiveDB")
	public DataSource archiveDatasource() {
		final HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(archiveDataSource.getUrl());
		ds.setUsername(archiveDataSource.getUsername());
		ds.setPassword(archiveDataSource.getPassword());
		
		ds.setMaximumPoolSize(archiveDataSource.getMaximumPoolSize());
		ds.setMinimumIdle(archiveDataSource.getMinimumIdle());
		ds.setConnectionTimeout(archiveDataSource.getConnectionTimeout());
		ds.setIdleTimeout(archiveDataSource.getIdleTimeout());
		return ds;
	}

}