# zabbix-sender
java zabbix-sender

First you should know zabbix sender:

https://www.zabbix.com/documentation/6.4/en/manual/concepts/sender

https://www.zabbix.com/documentation/6.4/en/manual/appendix/items/trapper

If you don't have a zabbix server, recommend use docker to set up test environment.

https://hub.docker.com/u/zabbix/

Support zabbix server up to 6.4.


##Example

Zabbix Sender do not create host/item, you have to create them by yourself, or try to use [zabbix-api](https://github.com/hengyunabc/zabbix-api).

1. Create/select a host in zabbix server.
2. Create an item in zabbix server, which name is "testItem", type is "Zabbix trapper".
3. Send data.
4. If success, you can find data in web browser. Open "Monitoring"/"Latest data", then filter with Item name or Hosts.

```java
    JsonHandler jhandler = new JsonHandler() {
        public String serialize(Object data) {
            return JSON.toJSONString(data);
        }
        public <T> T deserialize(String content, Class<T> clazz) {
            return JSON.parseObject(content, clazz);
        }
    };

    String host = "127.0.0.1";
    int port = 10051;
    ZabbixSender zabbixClient = new ZabbixSender(host, port, jhandler);

    DataObject dataObject = DataObject.builder()
                                      .host("172.17.42.1")
                                      .key("healthcheck[dw,notificationserver]")
                                      .value("thevalue")
                                      .clock(Instant.now())
                                      .build();
    SenderResult result = zabbixClient.send(dataObject);

    System.out.println("result:" + result);
    if (result.success()) {
        System.out.println("send success.");
    } else {
        System.err.println("sned fail!");
    }
```

## Maven dependency

```xml
<dependency>
    <groupId>fr.loghub</groupId>
    <artifactId>zabbix-sender</artifactId>
    <version>0.0.6-SNAPSHOT</version>
</dependency>
```

## Others

https://github.com/hengyunabc/zabbix-api

## License
Apache License V2
