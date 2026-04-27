package org.styli.services.customer.gateway.getters;

import org.apache.commons.collections.MapUtils;

import java.util.Map;

/**
 * Created on 04-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public interface MapValueGetter {

    default Long getLongFromMap(Map<?, ?> map, Object key) {
        return getLongFromMap(map, key, null);
    }
    default Long getLongFromMap(Map<?, ?> map, Object key, Long fallback) {
        Long result = fallback;
        if(MapUtils.isNotEmpty(map) && map.get(key) instanceof Number) {
			result = Long.valueOf(map.get(key) + "");
        }
        return result;
    }


    default Integer getIntFromMap(Map<?, ?> map, Object key) {
        return getIntFromMap(map, key, null);
    }
    default Integer getIntFromMap(Map<?, ?> map, Object key, Integer fallback) {
        Integer result = fallback;
        if(MapUtils.isNotEmpty(map) && map.get(key) instanceof Number) {
			result = Integer.valueOf(map.get(key) + "");
        }
        return result;
    }

    default Boolean getBooleanFromMap(Map<?, ?> map, Object key) {
        return getBooleanFromMap(map, key, false);
    }
    default Boolean getBooleanFromMap(Map<?, ?> map, Object key, Boolean fallback) {
        Boolean result = fallback;
        if(MapUtils.isNotEmpty(map) && map.get(key) instanceof Boolean) {
            result = Boolean.valueOf(map.get(key) + "");
        }
        return result;
    }


    default String getStringFromMap(Map map, Object key) {
        return getStringFromMap(map, key, null);
    }
    default String getStringFromMap(Map map, Object key, String fallback) {
        String result = fallback;
        if(MapUtils.isNotEmpty(map) && map.get(key) != null) {
            result = map.get(key).toString();
        }
        return result;
    }
}
