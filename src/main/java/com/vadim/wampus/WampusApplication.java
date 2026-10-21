package com.vadim.wampus;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WampusApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(WampusApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
