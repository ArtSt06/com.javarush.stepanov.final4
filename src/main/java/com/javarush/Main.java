package com.javarush;

import com.javarush.config.AppConfig;
import com.javarush.dao.CityDAO;
import com.javarush.dao.CountryDAO;
import com.javarush.service.ComparisonService;
import com.javarush.service.DataPreparationService;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        AppConfig config = new AppConfig();

        try {
            CityDAO cityDAO = new CityDAO(config.getSessionFactory());
            CountryDAO countryDAO = new CountryDAO(config.getSessionFactory());

            DataPreparationService preparationService = new DataPreparationService(
                    config.getSessionFactory(),
                    cityDAO,
                    countryDAO,
                    config.getRedisClient(),
                    config.getJsonUtil()
            );
            preparationService.prepareAndFillRedis();

            ComparisonService comparisonService = new ComparisonService(
                    config.getSessionFactory(),
                    cityDAO,
                    config.getRedisClient(),
                    config.getJsonUtil()
            );

            List<Integer> testIds = List.of(3, 2545, 123, 4, 189, 89, 3458, 1189, 10, 102);
            comparisonService.compareDetailed(testIds, 3);
        } finally {
            config.close();
        }
    }
}