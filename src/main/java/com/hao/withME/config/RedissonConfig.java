package com.hao.withME.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置
 */
@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedissonConfig {

    private String host;

    private String port;

    // 1. 新增：必须定义 password 字段，Spring 才能把 yml 里的值注入进来
    private String password;

    // 建议：把 database 也做成配置，不要硬编码
    private Integer database;

    @Bean
    public RedissonClient redissonClient() {
        // 1. 创建配置
        Config config = new Config();
        String redisAddress = String.format("redis://%s:%s", host, port);

        // 2. 设置地址、数据库和密码
        config.useSingleServer()
                .setAddress(redisAddress)
                // 注意：这里原本写死了 .setDatabase(3)，建议改为读取配置
                // 如果你的 yml 里 database 是 1，这里硬编码 3 会导致连错库
                .setDatabase(database != null ? database : 3)
                .setPassword(password); // 3. 关键修复：设置密码

        // 3. 创建实例
        RedissonClient redisson = Redisson.create(config);
        return redisson;
    }
}