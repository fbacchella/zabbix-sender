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
    public void key0() {
        DataObject.Builder builder = DataObject.builder().key("healthcheck");
        checkKey(builder, "healthcheck");
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
    public void keybad1() {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> DataObject.builder().key(null, List.of("keyname")));
        Assert.assertEquals("Invalid key definition", ex.getMessage());
    }

    @Test
    public void keybad2() {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> DataObject.builder().key("", List.of("keyname")));
        Assert.assertEquals("Invalid key definition", ex.getMessage());
    }

    @Test
    public void keybad3() {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> DataObject.builder().key(null, List.of("dw", "notificationserver")));
        Assert.assertEquals("Invalid key definition", ex.getMessage());
    }

    @Test
    public void keybad4() {
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, () -> DataObject.builder().key(null, Stream.empty()));
        Assert.assertEquals("Invalid key definition", ex.getMessage());
    }

}
