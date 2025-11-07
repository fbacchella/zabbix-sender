package fr.loghub.zabbix.sender;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Data
public class DataObject {

    @Accessors(fluent = true)
    public static class Builder {
        @Setter
        private Instant clock = Instant.now();
        @Setter
        private String host;
        private  String key;
        @Setter
        private Object value;
        private Builder() {
        }
        public Builder key(String name, Object... elements) {
            if (elements.length == 0) {
                return keyResolver(name, null);
            } else {
                return keyResolver(name, Arrays.stream(elements));
            }
        }
        public Builder key(String name, List<?> elements) {
            return keyResolver(name, elements.stream());
        }
        public Builder key(String name, Stream<?> elements) {
            return keyResolver(name, elements);
        }
        private Builder keyResolver(String name, Stream<?> elements) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Invalid key definition");
            } else if (elements == null) {
                key = name;
            } else {
                String values = elements.map(Object::toString).collect(Collectors.joining(","));
                if (values.isBlank()) {
                    throw new IllegalArgumentException("Invalid key definition");
                } else {
                    key = String.format("%s[%s]", name, values);
                }
            }
            return this;
        }
        public DataObject build() {
            return new DataObject(this);
        }
    }
    public static Builder builder() {
        return new Builder();
    }

    @Getter
    private final Instant clock;
    @Getter
    private final String host;
    @Getter
    private final String key;
    @Getter
    private final Object value;

    private DataObject(Builder builder) {
        clock = builder.clock;
        host = builder.host;
        key = builder.key;
        value = builder.value;
    }

    public Object getJsonObject() {
        return Map.of("host", host,
                      "key", key,
                      "value", value,
                      "clock", clock.getEpochSecond(),
                      "ns", clock.getNano());
    }


}
