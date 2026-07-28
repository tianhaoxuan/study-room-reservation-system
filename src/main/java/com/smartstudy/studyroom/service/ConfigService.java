package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConfigService {

    private final SystemConfigMapper systemConfigMapper;

    public ConfigService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    public int getIntConfig(String key, int defaultValue) {
        String value = systemConfigMapper.findValue(key);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
