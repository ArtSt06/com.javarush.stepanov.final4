package com.javarush;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javarush.dao.CityDAO;
import com.javarush.dao.CountryDAO;
import com.javarush.domain.*;
import com.javarush.redis.CityCountry;
import com.javarush.redis.Language;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

public class Main {

    private final SessionFactory sessionFactory;
    private final RedisClient redisClient;
    private final ObjectMapper objectMapper;
    private final CityDAO cityDAO;
    private final CountryDAO countryDAO;

    public Main() {
        sessionFactory = prepareRelationalDb();
        cityDAO = new CityDAO(sessionFactory);
        countryDAO = new CountryDAO(sessionFactory);
        redisClient = prepareRedisClient();
        objectMapper = new ObjectMapper();
    }

    private SessionFactory prepareRelationalDb() {
        Properties properties = new Properties();
        properties.put(Environment.DIALECT, "org.hibernate.dialect.MySQL8Dialect");
        properties.put(Environment.DRIVER, "com.p6spy.engine.spy.P6SpyDriver");
        properties.put(Environment.URL, "jdbc:p6spy:mysql://localhost:3306/world");
        properties.put(Environment.USER, "root");
        properties.put(Environment.PASS, "root");
        properties.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        properties.put(Environment.HBM2DDL_AUTO, "validate");
        properties.put(Environment.STATEMENT_BATCH_SIZE, "100");

        return new Configuration()
                .addAnnotatedClass(City.class)
                .addAnnotatedClass(Country.class)
                .addAnnotatedClass(CountryLanguage.class)
                .addProperties(properties)
                .buildSessionFactory();
    }

    private RedisClient prepareRedisClient() {
        RedisClient client = RedisClient.create(RedisURI.create("localhost", 6379));
        try (StatefulRedisConnection<String, String> conn = client.connect()) {
            System.out.println("Connected to Redis");
        }
        return client;
    }

    private List<City> fetchData() {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();

            List<Country> countries = countryDAO.getAll();

            int total = cityDAO.getTotalCount();
            int step = 500;
            List<City> allCities = new ArrayList<>();
            for (int i = 0; i < total; i += step) {
                allCities.addAll(cityDAO.getItems(i, step));
            }

            session.getTransaction().commit();
            return allCities;
        }
    }

    private List<CityCountry> transformData(List<City> cities) {
        return cities.stream().map(city -> {
            CityCountry cc = new CityCountry();
            cc.setId(city.getId());
            cc.setName(city.getName());
            cc.setPopulation(city.getPopulation());
            cc.setDistrict(city.getDistrict());

            Country country = city.getCountry();
            cc.setAlternativeCountryCode(country.getAlternativeCode());
            cc.setContinent(country.getContinent());
            cc.setCountryCode(country.getCode());
            cc.setCountryName(country.getName());
            cc.setCountryPopulation(country.getPopulation());
            cc.setCountryRegion(country.getRegion());
            cc.setCountrySurfaceArea(country.getSurfaceArea());

            Set<Language> languages = country.getLanguages().stream().map(cl -> {
                Language lang = new Language();
                lang.setLanguage(cl.getLanguage());
                lang.setIsOfficial(cl.getIsOfficial());
                lang.setPercentage(cl.getPercentage());
                return lang;
            }).collect(Collectors.toSet());
            cc.setLanguages(languages);

            return cc;
        }).collect(Collectors.toList());
    }

    private void pushToRedis(List<CityCountry> data) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisStringCommands<String, String> sync = connection.sync();
            for (CityCountry cc : data) {
                try {
                    String json = objectMapper.writeValueAsString(cc);
                    sync.set(String.valueOf(cc.getId()), json);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void testRedisData(List<Integer> ids) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisStringCommands<String, String> sync = connection.sync();
            for (Integer id : ids) {
                String json = sync.get(String.valueOf(id));
                if (json != null) {
                    try {
                        objectMapper.readValue(json, CityCountry.class);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void testMysqlData(List<Integer> ids) {
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            for (Integer id : ids) {
                City city = cityDAO.getById(id);
                city.getCountry().getLanguages().size();
            }
            session.getTransaction().commit();
        }
    }

    // Закрытие ресурсов
    private void shutdown() {
        if (nonNull(sessionFactory)) {
            sessionFactory.close();
        }
        if (nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }

    public static void main(String[] args) {
        Main main = new Main();

        List<City> allCities = main.fetchData();

        List<CityCountry> prepared = main.transformData(allCities);

        main.pushToRedis(prepared);

        main.sessionFactory.getCurrentSession().close();

        List<Integer> testIds = List.of(3, 2545, 123, 4, 189, 89, 3458, 1189, 10, 102);

        long startRedis = System.currentTimeMillis();
        main.testRedisData(testIds);
        long endRedis = System.currentTimeMillis();

        long startMysql = System.currentTimeMillis();
        main.testMysqlData(testIds);
        long endMysql = System.currentTimeMillis();

        System.out.printf("Redis: %d ms\n", endRedis - startRedis);
        System.out.printf("MySQL: %d ms\n", endMysql - startMysql);

        main.shutdown();
    }
}