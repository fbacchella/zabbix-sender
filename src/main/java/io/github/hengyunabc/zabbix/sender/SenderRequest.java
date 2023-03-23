package io.github.hengyunabc.zabbix.sender;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 *
 * @author hengyunabc
 *
 */
@Builder
class SenderRequest {

    @Getter @Builder.Default
    private final String request = "sender data";

    @Getter
    private final Instant clock;

    @Getter @Singular("data")
    private List<DataObject> data;

    public Map<String, Object> getContent() {
        // https://www.zabbix.com/documentation/current/en/manual/appendix/items/trapper

        Map<String, Object> content = new LinkedHashMap<>(8);
        content.put("request", request);
        content.put("data", data.stream().map(DataObject::getContent).collect(Collectors.toList()));
        content.put("clock", clock.getEpochSecond());
        content.put("ns", clock.getNano());

        return Collections.unmodifiableMap(content);
    }

}
