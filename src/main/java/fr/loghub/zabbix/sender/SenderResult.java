package fr.loghub.zabbix.sender;

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

    @Getter @Builder.Default
    private final int processed = - 1;
    @Getter @Builder.Default
    private final int failed = -1;
    @Getter @Builder.Default
    private final int total = -1;
    @Getter @Builder.Default
    private final float spentSeconds = -1.0f;

    /**
     * if all sent data are processed, will return true, else return false.
     *
     * @return true if processing was a success
     */
    public boolean success() {
        return total >= 0 && processed == total;
    }

}
