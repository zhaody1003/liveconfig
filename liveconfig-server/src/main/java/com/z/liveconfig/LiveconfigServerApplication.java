package com.z.liveconfig;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.z.liveconfig.mapper")
public class LiveconfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveconfigServerApplication.class, args);
    }

}
