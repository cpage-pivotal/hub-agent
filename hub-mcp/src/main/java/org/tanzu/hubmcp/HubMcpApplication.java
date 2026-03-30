package org.tanzu.hubmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.tanzu.goose.cf.broker.BrokerAutoConfiguration;
import org.tanzu.goose.cf.spring.GooseAutoConfiguration;

@SpringBootApplication(exclude = {GooseAutoConfiguration.class, BrokerAutoConfiguration.class})
@ConfigurationPropertiesScan
public class HubMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubMcpApplication.class, args);
    }

}
