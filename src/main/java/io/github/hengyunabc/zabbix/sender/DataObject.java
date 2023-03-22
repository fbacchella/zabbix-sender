package io.github.hengyunabc.zabbix.sender;

import com.alibaba.fastjson.JSON;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.Builder.Default;

@Builder
public class DataObject {

    @Getter @Builder.Default
    private final long clock = System.currentTimeMillis() / 1000;
    @Getter
    private final String host;
    @Getter
    private final String key;
    @Getter
    private final String value;

    @Override
    public String toString(){
        return JSON.toJSONString(this);
    }

}
