package fr.loghub.zabbix.sender;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Singular;

/**
 *
 * @author hengyunabc
 *
 */
@Builder @Data
class SenderRequest {

    @Getter @Builder.Default
    private final String request = "sender data";

    @Getter @Builder.Default
    private final Instant clock = Instant.now();

    @Getter @Singular("data")
    private final List<DataObject> data;

    public Map<String, Object> getJsonObject() {
        // https://www.zabbix.com/documentation/current/en/manual/appendix/items/trapper
        return Map.of("request", request,
                      "data", data.stream().map(DataObject::getJsonObject).collect(Collectors.toList()),
                      "clock", clock.getEpochSecond(),
                      "ns", clock.getNano());
    }

}
