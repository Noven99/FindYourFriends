package com.hao.withME;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.hao.withME.mapper")
@EnableScheduling
public class WithMEApplication {

    public static void main(String[] args) {
        SpringApplication.run(WithMEApplication.class, args);
    }

}
