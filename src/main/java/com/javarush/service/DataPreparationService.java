package com.javarush.service;

import com.javarush.dao.CityDAO;
import com.javarush.dao.CountryDAO;
import com.javarush.domain.City;
import com.javarush.domain.Country;
import com.javarush.domain.CountryLanguage;
import com.javarush.redis.CityCountry;
import com.javarush.redis.Language;
import com.javarush.util.JsonUtil;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DataPreparationService {
    private static final int BATCH_SIZE = 500;

    private final SessionFactory sessionFactory;
    private final CityDAO cityDAO;
    private final CountryDAO countryDAO;
    private final RedisClient redisClient;
    private final JsonUtil jsonUtil;

    public DataPreparationService(SessionFactory sessionFactory,
                                  CityDAO cityDAO,
                                  CountryDAO countryDAO,
                                  RedisClient redisClient,
                                  JsonUtil jsonUtil) {
        this.sessionFactory = sessionFactory;
        this.cityDAO = cityDAO;
        this.countryDAO = countryDAO;
        this.redisClient = redisClient;
        this.jsonUtil = jsonUtil;
    }

    public void prepareAndFillRedis() {
        List<CityCountry> data;
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            countryDAO.getAll();
            data = loadAndPrepareData();
            session.getTransaction().commit();
        }
        saveAllToRedis(data);
    }

    private List<CityCountry> loadAndPrepareData() {
        int total = cityDAO.getTotalCount();
        List<City> cities = new ArrayList<>(total);
        for (int i = 0; i < total; i += BATCH_SIZE) {
            cities.addAll(cityDAO.getItems(i, BATCH_SIZE));
        }
        return cities.stream()
                .map(this::toCityCountry)
                .toList();
    }

    private void saveAllToRedis(List<CityCountry> data) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            for (CityCountry item : data) {
                commands.set(String.valueOf(item.getId()), jsonUtil.toJson(item));
            }
        }
    }

    private CityCountry toCityCountry(City city) {
        Country country = city.getCountry();
        Set<Language> languages = country.getLanguages().stream()
                .map(this::toLanguage)
                .collect(Collectors.toSet());

        return CityCountry.builder()
                .id(city.getId())
                .name(city.getName())
                .district(city.getDistrict())
                .population(city.getPopulation())
                .countryCode(country.getCode())
                .alternativeCountryCode(country.getAlternativeCode())
                .countryName(country.getName())
                .continent(country.getContinent())
                .countryRegion(country.getRegion())
                .countrySurfaceArea(country.getSurfaceArea())
                .countryPopulation(country.getPopulation())
                .languages(languages)
                .build();
    }

    private Language toLanguage(CountryLanguage cl) {
        return Language.builder()
                .language(cl.getLanguage())
                .isOfficial(cl.getIsOfficial())
                .percentage(cl.getPercentage())
                .build();
    }
}