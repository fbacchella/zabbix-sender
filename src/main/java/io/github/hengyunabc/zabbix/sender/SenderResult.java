package io.github.hengyunabc.zabbix.sender;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 *
 * @author hengyunabc
 *
 */
@Builder @Data
public class SenderResult {
    @Getter
    private final int processed;
    @Getter
    private final int failed;
    @Getter
    private final int total;
    @Getter
    private final float spentSeconds;

    /**
     * sometimes zabbix server will return "[]".
     */
    @Getter @Builder.Default
    private final boolean returnEmptyArray = false;

    /**
     * if all sended data are processed, will return true, else return false.
     *
     * @return
     */
    public boolean success() {
        return !returnEmptyArray && processed == total;
    }

}
