package com.sap.lsp.cf.ws;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;

class LangServerCtx extends HashMap<String, String> {

    /**
     *
     */
    private static final long serialVersionUID = 6250146579876230161L;
    static final String ENV_RPCTYPE = "protocol";
    static final String LAUNCHER_FILE_NAME = "launcher.sh";

    private static final Logger LOG = Logger.getLogger(LangServerCtx.class.getName());

    private String workdir;

    LangServerCtx(String languageId, String workdir) {
        this.workdir = workdir;
        String prefix = LangPrefix(languageId);
        System.getenv().forEach((envVar, value) -> {
            if (envVar.startsWith(prefix)) {
                put(envVar.substring(prefix.length(), envVar.length()), value);
            }
        });
    }

    ProcessBuilder getProcessBuilder(String[] wsKeyElem) throws LSPException {
        String launcherScriptPath = FilenameUtils.concat(this.workdir, LAUNCHER_FILE_NAME);
        File launcherFile = new File(launcherScriptPath);
        if (!launcherFile.exists()) {
            LOG.warning("Launcher script was not found in: " + launcherScriptPath);
            throw new LSPConfigurationException();
        }        

        List<String> cmd = new ArrayList<>();
        if (System.getProperty("os.name").substring(0, 3).equalsIgnoreCase("win")) {
            cmd.add("cmd.exe");
            cmd.add("/C");
        } else {
            cmd.add("/bin/bash");
        }
        cmd.add(launcherScriptPath);
        Collections.addAll(cmd, wsKeyElem);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(this.workdir));
        pb.redirectErrorStream(true);

        return pb;
    }

    String getRpcType() {
        return get(ENV_RPCTYPE);
    }

    static String LangPrefix(String languageId) {
        return "LSP" + languageId.toUpperCase() + "_";
    }

}
