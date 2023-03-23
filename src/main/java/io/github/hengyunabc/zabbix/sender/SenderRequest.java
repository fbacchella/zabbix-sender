package io.github.hengyunabc.zabbix.sender;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;

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
    private static final byte[] header = { 'Z', 'B', 'X', 'D', '\1' };

    @Getter @Builder.Default
    private final String request = "sender data";

    /**
     * TimeUnit is SECONDS.
     */
    @Getter
    private final Instant clock;

    @Getter @Singular("data")
    private List<DataObject> data;

    public byte[] toBytes() {
        // https://www.zabbix.com/documentation/current/en/manual/appendix/items/trapper

        Map<String, Object> content = new LinkedHashMap<>(8);
        content.put("request", request);
        content.put("data", data.stream().map(d -> d.getContent()).collect(Collectors.toList()));
        content.put("clock", clock.getEpochSecond());
        content.put("ns", clock.getNano());

        byte[] jsonBytes = JSON.toJSONBytes(content);

        byte[] result = new byte[header.length + 4 + 4 + jsonBytes.length];

        System.arraycopy(header, 0, result, 0, header.length);

        result[header.length] = (byte) (jsonBytes.length & 0xFF);
        result[header.length + 1] = (byte) ((jsonBytes.length >> 8) & 0x00FF);
        result[header.length + 2] = (byte) ((jsonBytes.length >> 16) & 0x0000FF);
        result[header.length + 3] = (byte) ((jsonBytes.length >> 24) & 0x000000FF);
        result[header.length + 4] = (byte) 0;
        result[header.length + 5] = (byte) 0;
        result[header.length + 6] = (byte) 0;
        result[header.length + 7] = (byte) 0;

        System.arraycopy(jsonBytes, 0, result, header.length + 4 + 4, jsonBytes.length);
        return result;
    }

}
