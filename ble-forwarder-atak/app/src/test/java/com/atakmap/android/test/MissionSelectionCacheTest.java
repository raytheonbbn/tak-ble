package com.atakmap.android.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MissionSelectionCacheTest {
    @Test
    public void serializationTest00() throws JsonProcessingException {
        int index = 1;
        List<MissionListSpinnerItem> listItemNames = new ArrayList<>();
        listItemNames.add(new MissionListSpinnerItem("1", "A"));
        listItemNames.add(new MissionListSpinnerItem("2", "B"));
        listItemNames.add(new MissionListSpinnerItem("3", "C"));
        MissionSelectionCache cache = new MissionSelectionCache(index, listItemNames);
        ObjectMapper mapper = new ObjectMapper();
        String result = mapper.writeValueAsString(cache);
        System.out.println("Serialized result: " + result);
        MissionSelectionCache cache1 = mapper.readValue(result, MissionSelectionCache.class);
        System.out.println("Original: " + cache);
        System.out.println("New: " + cache1);
        Assert.assertEquals(cache, cache1);
    }

    @Test
    public void serializationTest01() throws JsonProcessingException {
        int index = 1;
        List<MissionListSpinnerItem> listItemNames = null;
        MissionSelectionCache cache = new MissionSelectionCache(index, listItemNames);
        ObjectMapper mapper = new ObjectMapper();
        String result = mapper.writeValueAsString(cache);
        System.out.println("Serialized result: " + result);
        MissionSelectionCache cache1 = mapper.readValue(result, MissionSelectionCache.class);
        System.out.println("Original: " + cache);
        System.out.println("New: " + cache1);
        Assert.assertEquals(cache, cache1);
    }
}
