package com.vadim.wampus;

import com.vadim.wampus.config.WampusProperties;
import com.vadim.wampus.ui.WampusFxApplication;
import javafx.application.Application;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WampusApplication {

    private static ConfigurableApplicationContext context;

    public static void main(String[] args) {
        context = new SpringApplicationBuilder(WampusApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        // в консольном режиме игра уже прошла в ConsoleRunner — окно не нужно
        if (context.getBean(WampusProperties.class).isConsole()) {
            closeContext();
            return;
        }
        Application.launch(WampusFxApplication.class, args);
    }

    public static <T> T getBean(Class<T> type) {
        return context.getBean(type);
    }

    public static void closeContext() {
        if (context != null) {
            context.close();
        }
    }
}
