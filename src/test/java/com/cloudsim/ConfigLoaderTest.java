package com.cloudsim;

import com.cloudsim.config.ConfigLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigLoaderTest {

    @Test
    void testConfigLoaderHandlesInvalidFile() {
        assertThrows(Exception.class, () -> {
            ConfigLoader.loadVmTemplates("invalid.json");
        });
    }
    @Test
    void testConfigLoaderValidFile() throws Exception {
        // Use your actual JSON file path
        String path = "src/main/java/com/cloudsim/config/vm_types.json";

        assertDoesNotThrow(() -> {
            ConfigLoader.loadVmTemplates(path);
        });
    }
}