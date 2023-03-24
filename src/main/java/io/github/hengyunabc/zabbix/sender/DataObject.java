package io.github.hengyunabc.zabbix.sender;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Builder @Data
public class DataObject {

    @Getter @Builder.Default
    private final Instant clock = Instant.now();
    @Getter
    private final String host;
    @Getter
    private final String key;
    @Getter
    private final String value;

    public Object getContent() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("host", host);
        content.put("key", key);
        content.put("value", value);
        content.put("clock", clock.getEpochSecond());
        content.put("ns", clock.getNano());
        return Collections.unmodifiableMap(content);
    }

}
