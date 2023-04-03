package fr.loghub.zabbix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class ZabbixProtocolTest {
    @Test
    public void testByteOrder() {
        ByteBuffer bb = ByteBuffer.wrap(new byte[4]);
        byte[] buffer = new byte[4];
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (int i: List.of(1, 256, 65536, 256 * 65536)) {
            bb.clear();
            bb.putInt(i);
            ZabbixProtocol.writeInt(i, buffer, 0);
            int j = ZabbixProtocol.readInt(buffer, 0);
            Assert.assertEquals(j, i);
        }
    }
}
