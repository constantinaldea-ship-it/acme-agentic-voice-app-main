package com.voicebanking.poc.eventstore;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Activates the POC Event Store module only when the "poc" profile is active.
 * Re-imports DataSource and JPA autoconfigurations that are excluded globally
 * to keep the non-POC startup free of database dependencies.
 */
@Configuration
@Profile("poc")
@Import({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ComponentScan(basePackages = "com.voicebanking.poc.eventstore")
@EnableJpaRepositories(basePackages = "com.voicebanking.poc.eventstore.repository")
@EntityScan(basePackages = "com.voicebanking.poc.eventstore.model")
public class EventStoreAutoConfiguration {
}
