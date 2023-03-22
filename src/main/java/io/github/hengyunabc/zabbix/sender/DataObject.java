package io.github.hengyunabc.zabbix.sender;

import com.alibaba.fastjson.JSON;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.Builder.Default;

@Builder
public class DataObject {

    @Getter @Setter @Default
    long clock = System.currentTimeMillis() / 1000;
    @Getter @Setter
    String host;
    @Getter @Setter
    String key;
    @Getter @Setter
    String value;

    public DataObject() {

    }

    public DataObject(long clock, String host, String key, String value) {
        this.clock = clock;
        this.host = host;
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString(){
        return JSON.toJSONString(this);
    }

}
