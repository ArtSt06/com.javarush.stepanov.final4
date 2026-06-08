package com.javarush.service;

import com.javarush.dao.CityDAO;
import com.javarush.domain.City;
import com.javarush.util.JsonUtil;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.Arrays;
import java.util.List;

public class ComparisonService {
    private final SessionFactory sessionFactory;
    private final CityDAO cityDAO;
    private final RedisClient redisClient;
    private final JsonUtil jsonUtil;

    public ComparisonService(SessionFactory sessionFactory,
                             CityDAO cityDAO,
                             RedisClient redisClient,
                             JsonUtil jsonUtil) {
        this.sessionFactory = sessionFactory;
        this.cityDAO = cityDAO;
        this.redisClient = redisClient;
        this.jsonUtil = jsonUtil;
    }

    public void compareDetailed(List<Integer> ids, int attempts) {
        long[] redisTimes = new long[attempts];
        long[] mysqlTimes = new long[attempts];

        for (int i = 0; i < attempts; i++) {
            redisTimes[i] = measureRedis(ids);
            mysqlTimes[i] = measureMysql(ids);
            System.out.printf("Замер %d: Redis = %d ms, MySQL = %d ms%n",
                    i + 1, redisTimes[i], mysqlTimes[i]);
        }

        double avgRedis = Arrays.stream(redisTimes).average().orElse(0);
        double avgMysql = Arrays.stream(mysqlTimes).average().orElse(0);

        System.out.println("\n========== РЕЗУЛЬТАТ ==========");
        System.out.printf("Среднее время Redis:  %.2f ms%n", avgRedis);
        System.out.printf("Среднее время MySQL: %.2f ms%n", avgMysql);
        System.out.println("================================\n");
    }

    private long measureRedis(List<Integer> ids) {
        long start = System.currentTimeMillis();
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            for (Integer id : ids) {
                String json = commands.get(String.valueOf(id));
                if (json != null) {
                    jsonUtil.fromJson(json);
                }
            }
        }
        return System.currentTimeMillis() - start;
    }

    private long measureMysql(List<Integer> ids) {
        long start = System.currentTimeMillis();
        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            for (Integer id : ids) {
                City city = cityDAO.getById(id);
                city.getCountry().getLanguages().size();
            }
            session.getTransaction().commit();
        }
        return System.currentTimeMillis() - start;
    }
}