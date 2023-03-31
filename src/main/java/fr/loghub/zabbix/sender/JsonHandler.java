package fr.loghub.zabbix.sender;

public interface JsonHandler {

    String serialize(Object data);

    <T> T deserialize(String content, Class<T> clazz);

}
