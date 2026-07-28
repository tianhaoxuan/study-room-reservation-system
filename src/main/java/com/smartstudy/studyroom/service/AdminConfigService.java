package com.smartstudy.studyroom.service;

import com.smartstudy.studyroom.common.BizConstants;
import com.smartstudy.studyroom.entity.SystemConfig;
import com.smartstudy.studyroom.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminConfigService {

    private final SystemConfigMapper systemConfigMapper;

    public AdminConfigService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    public Map<String, Object> list() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("checkinLimitMinutes", 15);
        result.put("maxReservationPerDay", 2);
        result.put("violationLimit", 3);

        List<SystemConfig> configs = systemConfigMapper.findCoreConfigs();
        for (SystemConfig config : configs) {
            result.put(toApiKey(config.getConfigKey()), parseValue(config.getConfigValue()));
        }
        return result;
    }

    @Transactional
    public void update(Map<String, Object> configs) {
        if (configs == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : configs.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String dbKey = toDbKey(entry.getKey());
            SystemConfig config = systemConfigMapper.findByKey(dbKey);
            if (config == null) {
                config = new SystemConfig();
                config.setConfigKey(dbKey);
                config.setConfigValue(entry.getValue().toString());
                config.setDescription(description(dbKey));
                systemConfigMapper.insert(config);
            } else {
                config.setConfigValue(entry.getValue().toString());
                config.setDescription(description(dbKey));
                systemConfigMapper.updateByKey(config);
            }
        }
    }

    private Object parseValue(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return value;
        }
    }

    private String toDbKey(String key) {
        if ("checkinLimitMinutes".equals(key)) {
            return BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES;
        }
        if ("maxReservationPerDay".equals(key)) {
            return BizConstants.CONFIG_MAX_RESERVATION_PER_DAY;
        }
        if ("violationLimit".equals(key)) {
            return BizConstants.CONFIG_VIOLATION_LIMIT;
        }
        return key;
    }

    private String toApiKey(String key) {
        if (BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES.equals(key)) {
            return "checkinLimitMinutes";
        }
        if (BizConstants.CONFIG_MAX_RESERVATION_PER_DAY.equals(key)) {
            return "maxReservationPerDay";
        }
        if (BizConstants.CONFIG_VIOLATION_LIMIT.equals(key)) {
            return "violationLimit";
        }
        return key;
    }

    private String description(String key) {
        if (BizConstants.CONFIG_CHECKIN_LIMIT_MINUTES.equals(key)) {
            return "签到宽容时间";
        }
        if (BizConstants.CONFIG_MAX_RESERVATION_PER_DAY.equals(key)) {
            return "每日最大预约次数";
        }
        if (BizConstants.CONFIG_VIOLATION_LIMIT.equals(key)) {
            return "违规封禁阈值";
        }
        return "";
    }
}
