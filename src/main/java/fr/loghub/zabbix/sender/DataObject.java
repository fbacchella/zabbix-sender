package fr.loghub.zabbix.sender;

import java.time.Instant;
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

    public Object getJsonObject() {
        return Map.of("host", host,
                      "key", key,
                      "value", value,
                      "clock", clock.getEpochSecond(),
                      "ns", clock.getNano());
    }

}
