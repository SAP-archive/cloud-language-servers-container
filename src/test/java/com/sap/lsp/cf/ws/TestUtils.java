package com.sap.lsp.cf.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;

public class TestUtils {

    static <T> T getInternalState(Object cut, String field) {
        try {
            Field f = cut.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return (T)f.get(cut);
        } catch (Exception e) {
            throw new RuntimeException("Unable to set internal state on a private field. [...]", e);
        }
    }

    static FileInputStream getZipStream(String testData) throws FileNotFoundException {
        assert (new File(System.getProperty("user.dir") + File.separator + "src/test/javascript/resources/" + testData).exists());
        return new FileInputStream(System.getProperty("user.dir") + File.separator + "src/test/javascript/resources/" + testData);
    }
}
