package com.javarush.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javarush.redis.CityCountry;

public class JsonUtil {
    private final ObjectMapper mapper;

    public JsonUtil(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(CityCountry cityCountry) {
        try {
            return mapper.writeValueAsString(cityCountry);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize CityCountry", e);
        }
    }

    public CityCountry fromJson(String json) {
        try {
            return mapper.readValue(json, CityCountry.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize CityCountry", e);
        }
    }
}