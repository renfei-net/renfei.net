package net.renfei;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import springfox.documentation.oas.annotations.EnableOpenApi;

import java.util.TimeZone;

/**
 * 应用程序启动入口
 *
 * @author renfei
 */
@EnableAsync
@EnableCaching
@EnableOpenApi
@EnableScheduling
@SpringBootApplication
@EnableTransactionManagement
@EnableConfigurationProperties
@ServletComponentScan(basePackages = {"net.renfei.filter"})
public class Application {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(Application.class, args);
    }

}
