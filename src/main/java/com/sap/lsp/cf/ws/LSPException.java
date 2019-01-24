package com.sap.lsp.cf.ws;

import java.io.IOException;

class LSPException extends IOException {
    private static final long serialVersionUID = 5462154802820315035L;

    LSPException() {
        super();
    }

    LSPException(Throwable throwable) {
        super(throwable);
    }
}
