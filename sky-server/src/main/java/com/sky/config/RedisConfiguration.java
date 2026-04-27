package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        log.info("开始创建redis模块对象...");
        RedisTemplate<String,Object> redisTemplate = new RedisTemplate<>();
        //设置redis的连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //设置redis key的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        //设置redis value序列化器
        //加了下面这行代码会报错:Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed:
        // org.springframework.data.redis.serializer.SerializationException:
        // Could not write JSON: Java 8 date/time type `java.time.LocalDateTime` not supported by default:
        // add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling
        // (or disable `MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES`)
        // (through reference chain: java.util.ArrayList[0]->com.sky.vo.DishVO["updateTime"])] with root cause
//        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return redisTemplate;
    }
}
