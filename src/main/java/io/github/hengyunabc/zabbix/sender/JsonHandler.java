package io.github.hengyunabc.zabbix.sender;

public interface JsonHandler {

    String serialize(Object data);

    <T> T deserialize(String content, Class<T> clazz);

}
