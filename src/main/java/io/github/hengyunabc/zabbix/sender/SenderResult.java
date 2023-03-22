package io.github.hengyunabc.zabbix.sender;

import com.alibaba.fastjson.JSON;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author hengyunabc
 *
 */
public class SenderResult {
    @Getter @Setter
    int processed;
    @Getter @Setter
    int failed;
    @Getter @Setter
    int total;

    @Getter @Setter
    float spentSeconds;

    /**
     * sometimes zabbix server will return "[]".
     */
    @Getter @Setter
    boolean bReturnEmptyArray = false;

    /**
     * if all sended data are processed, will return true, else return false.
     *
     * @return
     */
    public boolean success() {
        return !bReturnEmptyArray && processed == total;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
