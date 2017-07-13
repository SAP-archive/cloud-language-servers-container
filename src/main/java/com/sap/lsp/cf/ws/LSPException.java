package com.sap.lsp.cf.ws;

import java.io.IOException;

public class LSPException extends IOException {
    LSPException() {
        super();
    }

    LSPException(Throwable throwable) {
        super(throwable);
    }
}
