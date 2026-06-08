package com.javarush.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javarush.domain.City;
import com.javarush.domain.Country;
import com.javarush.domain.CountryLanguage;
import com.javarush.util.JsonUtil;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private final SessionFactory sessionFactory;
    private final RedisClient redisClient;
    private final JsonUtil jsonUtil;

    public AppConfig() {
        this.sessionFactory = createSessionFactory();
        this.redisClient = createRedisClient();
        this.jsonUtil = new JsonUtil(new ObjectMapper());
    }

    private SessionFactory createSessionFactory() {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("hibernate.properties")) {
            if (input == null) throw new RuntimeException("hibernate.properties not found");
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load hibernate.properties", e);
        }
        return new Configuration()
                .addAnnotatedClass(City.class)
                .addAnnotatedClass(Country.class)
                .addAnnotatedClass(CountryLanguage.class)
                .addProperties(properties)
                .buildSessionFactory();
    }

    private RedisClient createRedisClient() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("redis.properties")) {
            if (input != null) props.load(input);
        } catch (IOException e) {
            System.err.println("redis.properties not found, using defaults");
        }
        String host = props.getProperty("redis.host", "localhost");
        int port = Integer.parseInt(props.getProperty("redis.port", "6379"));
        return RedisClient.create(RedisURI.create(host, port));
    }

    public SessionFactory getSessionFactory() { return sessionFactory; }
    public RedisClient getRedisClient() { return redisClient; }
    public JsonUtil getJsonUtil() { return jsonUtil; }

    public void close() {
        if (sessionFactory != null) sessionFactory.close();
        if (redisClient != null) redisClient.shutdown();
    }
}