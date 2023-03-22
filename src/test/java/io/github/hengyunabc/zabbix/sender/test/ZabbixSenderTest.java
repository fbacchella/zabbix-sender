package io.github.hengyunabc.zabbix.sender.test;

import io.github.hengyunabc.zabbix.sender.DataObject;
import io.github.hengyunabc.zabbix.sender.SenderResult;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import com.alibaba.fastjson.JSONObject;

public class ZabbixSenderTest {

    String host = "127.0.0.1";
    int port = 49156;

    @Test
    public void test_LLD_rule() throws IOException {
        ZabbixSender zabbixSender = new ZabbixSender(host, port);

        DataObject dataObject = new DataObject();
        dataObject.setHost("172.17.42.1");
        dataObject.setKey("healthcheck[dw,notificationserver]");

        JSONObject data = new JSONObject();
        List<JSONObject> aray = new LinkedList<JSONObject>();
        JSONObject xxx = new JSONObject();
        xxx.put("hello", "hello");

        aray.add(xxx);
        data.put("data", aray);

        dataObject.setValue(data.toJSONString());
        dataObject.setClock(System.currentTimeMillis()/1000);
        SenderResult result = zabbixSender.send(dataObject);

        System.out.println("result:" + result);
        if (result.success()) {
            System.out.println("send success.");
        } else {
            System.err.println("send fail!");
        }


    }

    @Test
    public void test() throws IOException {
        ZabbixSender zabbixSender = new ZabbixSender(host, port);

        DataObject dataObject = new DataObject();
        dataObject.setHost("172.17.42.1");
        dataObject.setKey("a[test, jvm.mem.non-heap.used]");
        dataObject.setValue("10");
        dataObject.setClock(System.currentTimeMillis()/1000);
        SenderResult result = zabbixSender.send(dataObject);

        System.out.println("result:" + result);
        if (result.success()) {
            System.out.println("send success.");
        } else {
            System.err.println("send fail!");
        }


    }

}
