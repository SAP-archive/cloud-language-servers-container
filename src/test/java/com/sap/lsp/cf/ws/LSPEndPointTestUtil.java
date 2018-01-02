package com.sap.lsp.cf.ws;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

final class LSPEndPointTestUtil {

    private static final String LANG = "aLang";
    private Path workdir;
    private Path basePath;
    private Path wdPath;
    private Map<String, LangServerCtx> ctx;

    LSPEndPointTestUtil() {

    }

    String createInfra() throws IOException {

        this.basePath = Files.createTempDirectory("java-build-pack");
        this.wdPath = Files.createTempDirectory(basePath, "di_ws_");
        this.workdir = Files.createTempDirectory(basePath, "workdir");
        Path launcher = Files.createFile(new File(basePath.toString() + "/Launcher.sh").toPath());
        // Return Log msg
        return String.format("TEST Env:\nBasePath=%s\nWorkdir=%s\nLauncher script=%s\n ",
                basePath.toString(), workdir.toString(), launcher.toString());

    }

    void MockServerContext() {
        LangServerCtx aLangCtx = new LangServerCtx("aLang");
        aLangCtx.put(LangServerCtx.ENV_LSP_WORKDIR, workdir.getName(workdir.getNameCount() - 1).toString());
        aLangCtx.put(LangServerCtx.ENV_LAUNCHER, "Launcher.sh");
        aLangCtx.put(LangServerCtx.ENV_RPCTYPE, "stream");
        aLangCtx.setBaseDir(basePath.toString() + File.separator);
        ctx = Collections.singletonMap(LANG, aLangCtx);

    }

    Path getWdPath() {
        return wdPath;
    }

    Map<String, LangServerCtx> getCtx() {
        return ctx;
    }


}
