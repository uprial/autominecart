package com.gmail.uprial.autominecart;

import com.gmail.uprial.autominecart.helpers.TestConfigBase;
import org.junit.Test;

public class AutoMinecartTest extends TestConfigBase {
    @Test
    public void testLoadException() throws Exception {
        AutoMinecart.loadConfig(getPreparedConfig(""), getCustomLogger());
    }
}