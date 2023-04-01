package fr.loghub.zabbix.sender;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

public class DataObjectTest {

    private void checkKey(DataObject.Builder builder, String expected) {
        DataObject dataObject = builder.host("172.17.42.1")
                                       .value(List.of(1, 2))
                                       .clock(Instant.now())
                                       .build();
        Assert.assertEquals(expected, dataObject.getKey());
    }

    @Test
    public void key1() {
        DataObject.Builder builder = DataObject.builder().key("healthcheck", "dw", "notificationserver");
        checkKey(builder, "healthcheck[dw,notificationserver]");
    }

    @Test
    public void key2() {
        DataObject.Builder builder = DataObject.builder().key("healthcheck", List.of("dw", "notificationserver"));
        checkKey(builder, "healthcheck[dw,notificationserver]");
    }

    @Test
    public void key3() {
        DataObject.Builder builder = DataObject.builder().key("healthcheck", Stream.of("dw", "notificationserver"));
        checkKey(builder, "healthcheck[dw,notificationserver]");
    }

    @Test
    public void key4() {
        DataObject.Builder builder = DataObject.builder().key(null, List.of("keyname"));
        checkKey(builder, "keyname");
    }

    @Test
    public void key5() {
        DataObject.Builder builder = DataObject.builder().key("", List.of("keyname"));
        checkKey(builder, "keyname");
    }

    @Test
    public void keybad() {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> DataObject.builder().key(null, List.of("dw", "notificationserver")));
        Assert.assertEquals("Invalid key definition", ex.getMessage());
    }

}
