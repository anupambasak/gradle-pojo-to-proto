package io.github.anupambasak.gradle.dtos;

import lombok.Data;

import java.util.Map;

@Data
public class MapPojo {
    private Map<String, Integer> simpleMap;
    private Map<String, Address> complexMap;
}
