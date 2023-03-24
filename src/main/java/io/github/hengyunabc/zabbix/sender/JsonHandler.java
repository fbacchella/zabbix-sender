package io.github.hengyunabc.zabbix.sender;

import java.nio.ByteBuffer;

public interface JsonHandler {
    String serialize(Object data);
    <T> T deserialize(String content, Class<T> clazz);
}
