package com.sap.lsp.cf.ws;

import javax.websocket.RemoteEndpoint;
import javax.websocket.RemoteEndpoint.Basic;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

class LSPProcessManager {

    private static final String ENV_IPC_SOCKET = "socket";
    private static final String ENV_IPC_PIPES = "pipes";
    private static final String ENV_IPC_CLIENT = "socket-client";
    private static final String ENV_IPC_IN_PORT = "inport";
    private static final String ENV_IPC_OUT_PORT = "outport";
    private static final String ENV_PIPE_IN = "pipein";
    private static final String ENV_IPC_CLIENT_PORT = "clientport";
    private static final String WS_KEY_DELIMITER = "~";
    private static final String CHARSET_PREF = "charset=";
    private static final Logger LOG = Logger.getLogger(LSPProcessManager.class.getName());

    private static class OutputStreamHandler extends Thread {
        static final String CONTENT_LENGTH_HEADER = "Content-Length";
        static final String CONTENT_TYPE_HEADER = "Content-Type";
        static final String CRLF = "\r\n";

        private final Basic remote;
        private final InputStream out;
        private boolean keepRunning;

        private static class Headers {
            int contentLength = -1;
            String charset = StandardCharsets.UTF_8.name();
        }

        OutputStreamHandler(RemoteEndpoint.Basic remoteEndpointBasic, InputStream out) {
            this.remote = remoteEndpointBasic;
            this.out = out;
        }

        void fireError(Throwable exception) {
            LOG.warning(exception.getMessage());
        }

        void parseHeader(String line, Headers headers) {
            int sepIndex = line.lastIndexOf(':');
            if (sepIndex >= 0) {
                String key = line.substring(0, sepIndex).trim();
                if (key.endsWith(CONTENT_LENGTH_HEADER)) {
                    try {
                        headers.contentLength = Integer.parseInt(line.substring(sepIndex + 1).trim());
                    } catch (NumberFormatException e) {
                        fireError(e);
                    }
                } else if (key.endsWith(CONTENT_TYPE_HEADER)) {
                    int charsetIndex = line.indexOf(CHARSET_PREF);
                    if (charsetIndex >= 0) {
                        headers.charset = line.substring(charsetIndex + CHARSET_PREF.length()).trim();
                    }
                }
            }
        }

        void postMessage(InputStream out, Headers headers, StringBuilder msgBuffer) throws IOException {

            int contentLength = headers.contentLength;
            byte[] buffer = new byte[contentLength];
            int bytesRead = 0;

            while (bytesRead < contentLength) {
                int readResult = out.read(buffer, bytesRead, contentLength - bytesRead);
                if (readResult == -1)
                    throw new IOException("Unexpected end of message");
                bytesRead += readResult;
            }
            String msgContent = new String(buffer, headers.charset);
            msgBuffer.append(CRLF).append(CRLF).append(msgContent);
            LOG.info("LSP sends " + msgBuffer.toString());
            remote.sendText(msgBuffer.toString());

        }

        public void run() {
            LOG.info("LSP: Listening...");
            keepRunning = true;
            StringBuilder headerBuilder = null;
            StringBuilder debugBuilder = null;
            boolean newLine = false;
            Headers headers = new Headers();

            while (keepRunning) {
                try {
                    byte c = (byte) out.read();
                    if (c == -1) {
                        // End of input stream has been reached
                        Thread.sleep(100);
                    } else {
                        if (debugBuilder == null)
                            debugBuilder = new StringBuilder();
                        debugBuilder.append((char) c);
                        if (c == '\n') {
                            LOG.info(">>OUT: " + debugBuilder.toString());
                            if (headerBuilder != null && headerBuilder.toString().startsWith("SLF4J:")) {
                                fireError(new IllegalStateException(headerBuilder.toString()));
                                // Skip and reset
                                newLine = false;
                                headerBuilder = null;
                                debugBuilder = null;
                                continue;
                            }
                            if (newLine) {
                                // Two consecutive newlines have been read,
                                // which signals the start of the message
                                // content
                                if (headers.contentLength < 0) {
                                    fireError(new IllegalStateException("Missing header " + CONTENT_LENGTH_HEADER
                                            + " in input \"" + debugBuilder + "\""));
                                } else {
                                    postMessage(out, headers, headerBuilder);
                                    newLine = false;
                                    headerBuilder = null;
                                }
                                headers = new Headers();
                                debugBuilder = null;
                            } else if (headerBuilder != null) {
                                // A single newline ends a header line
                                parseHeader(headerBuilder.toString(), headers);
                                // headerBuilder = null;
                            }
                            newLine = true;
                        } else if (c != '\r') {
                            // Add the input to the current header line
                            if (headerBuilder == null)
                                headerBuilder = new StringBuilder();
                            headerBuilder.append((char) c);
                            newLine = false;
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    LOG.severe("Out stream handler error: " + e.toString());
                    keepRunning = false;
                }
            }
        }
    }

    /**
     * Responsible for putting the LSP process logs in the standard server log
     */
    private static class LogStreamHandler extends Thread {
        private final BufferedReader log;

        LogStreamHandler(InputStream log) {
            this.log = new BufferedReader(new InputStreamReader(log));
        }

        public void run() {
            LOG.info("LSP: Listening for log...");
            StringBuilder logBuilder = null;
            boolean keepRunning = true;
            while (keepRunning) {
                try {
                    int c = log.read();
                    if (c == -1)
                        // End of input stream has been reached
                        Thread.sleep(100);
                    else {
                        if (logBuilder == null)
                            logBuilder = new StringBuilder();
                        logBuilder.append((char) c);
                        if (c == '\n') {
                            //LOG.info(">>LOG" + logBuilder.toString());
                            logBuilder = null;
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    LOG.warning("Log error: " + e.toString());
                    keepRunning = false;
                }
            }
        }

    }

    static class LSPProcess {
        private enum IPC {
            SOCKET, NAMEDPIPES, STREAM, CLIENTSOCKET
        }

        ServerSocket serverSocketIn = null;
        ServerSocket serverSocketOut = null;
        Socket clientSocket = null;
        OutputStreamHandler outputHandler = null;
        LogStreamHandler logHandler = null;

        private IPC ipc = null;
        private Thread openCommunication = null;
        private int socketIn = 0;
        private int socketOut = 0;
        private int clientSocketPort = 0;
        private String pipeIn = null;
        private String pipeOut = null;
        private Socket sin;
        private Socket sout;
        private PrintWriter inWriter = null;
        private InputStream out = null;
        private Process process;
        private ProcessBuilder pb;
        private RemoteEndpoint.Basic remoteEndpoint = null;
        private String projPath = "";
        private final String ownerSessionId;
        private final String lang;

        protected String getLang() {
            return lang;
        }

        LSPProcess(String wsKeyElem[], String lang, ProcessBuilder pb, Basic remoteEndpoint, String ownerSessionId) {
            this.pb = pb;
            this.remoteEndpoint = remoteEndpoint;
            this.projPath = "/" + String.join("/", Arrays.copyOfRange(wsKeyElem, 1, wsKeyElem.length));
            this.lang = lang;
            this.ownerSessionId = ownerSessionId;
        }

        protected String getOwnerSessionId() {
            return ownerSessionId;
        }

        void run() throws LSPException {
            switch (this.ipc) {
                case SOCKET:
                    LOG.info("Using Socket for communication");

                    try {
                        serverSocketIn = new ServerSocket(this.socketIn);
                        serverSocketOut = new ServerSocket(this.socketOut);
                    } catch (IOException ex) {
                        LOG.warning("Error in Socket communication " + ex.toString());
                        throw new LSPException(ex);
                    }
                    openCommunication = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                sin = serverSocketIn.accept();
                                sout = serverSocketOut.accept();
                                inWriter = new PrintWriter(
                                        new BufferedWriter(new OutputStreamWriter(sin.getOutputStream())));
                                out = sout.getInputStream();
                            } catch (IOException ex) {
                                LOG.warning("Error in Socket communication " + ex.toString());
                            }
                        }
                    });
                    openCommunication.start();
                    break;

                case NAMEDPIPES:
                    LOG.info("Using named pipes communication");
                    String processIn = this.pipeIn;
                    String processOut = this.pipeOut;

                    openCommunication = new Thread(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                inWriter = new PrintWriter((new BufferedWriter(new FileWriter(processOut))));
                                out = new FileInputStream(processIn);
                            } catch (IOException pipeEx) {
                                LOG.warning("Error in pipes communication " + pipeEx.toString());
                            }
                        }

                    });

                    // Create pipes
                    try {
                        Process mkfifoProc = new ProcessBuilder("mkfifo", processIn).inheritIO().start();
                        int processUp = mkfifoProc.waitFor();
                        if (processUp != 0) {
                            LOG.warning("Process wait error");
                            throw new LSPException();
                        }
                        mkfifoProc = new ProcessBuilder("mkfifo", processOut).inheritIO().start();
                        processUp = mkfifoProc.waitFor();
                        if (processUp != 0) {
                            LOG.warning("Process wait error");
                            throw new LSPException();
                        }
                    } catch (IOException | InterruptedException mkfifoEx) {
                        LOG.severe("Pipe error: " + mkfifoEx.getMessage());
                    }
                    openCommunication.start();
                    break;

                case STREAM:
                    LOG.info("Using StdIn / StdOut streams");
            }

            try {
                process = pb.start();
                LOG.info("LSP Starting....");
                if (process.isAlive()) {
                    if (openCommunication != null) {
                        // Either Named pipes or Socket
                        openCommunication.join(30000L);
                        // TODO LOG output and err
                        logHandler = new LogStreamHandler(process.getInputStream());
                        logHandler.start();
                        switch (this.ipc) {
                            case SOCKET:
                                LOG.info("SocketIn " + this.serverSocketIn.toString() + " stat " + this.serverSocketIn.isBound());
                                LOG.info("SocketOut " + this.serverSocketOut.toString() + " stat " + this.serverSocketOut.isBound());
                                break;
                            case NAMEDPIPES:
                                LOG.info("PipeIn exists " + new File(this.pipeIn).exists());
                                LOG.info("PipeOut exists " + new File(this.pipeIn).exists());
                                break;
                            default:

                                break;
                        }
                    } else if (this.ipc == IPC.CLIENTSOCKET) {
                        LOG.info(String.format("LSP: attach to port %d", clientSocketPort));
                        Thread.sleep(500L);
                        this.clientSocket = new Socket("localhost", clientSocketPort);
                        inWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream())));
                        out = this.clientSocket.getInputStream();
                    } else {
                        // Stdin / Stdout
                        out = process.getInputStream();
                        OutputStream in = process.getOutputStream();
                        inWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(in)));
                    }
                    LOG.info("LSP: Server started");

                    outputHandler = new OutputStreamHandler(remoteEndpoint, out);
                    outputHandler.start();
                } else {
                    LOG.severe("LSP: Server start failure");
                    throw new LSPException();
                }

            } catch (InterruptedException | IOException e1) {
                LOG.severe("IO Exception while starting: " + e1.toString());
            }
        }

        void cleanup() {
            LOG.info("process cleanup");
            if (outputHandler != null && outputHandler.isAlive()) outputHandler.interrupt();
            if (logHandler != null && logHandler.isAlive()) logHandler.interrupt();

            // !! Process must be closed before closing the LSP out reader otherwise the out reader is stuck on close!!!
            if (process != null) {

                if (process.isAlive())
                    process.destroyForcibly();
                process = null;
            }

            try {
                if (inWriter != null) inWriter.close();
                if (out != null) out.close();
                if (sin != null) sin.close();
                if (sout != null) sout.close();
                if (this.serverSocketIn != null && !this.serverSocketIn.isClosed()) {
                    this.serverSocketIn.close();
                }
                if (this.serverSocketOut != null && !this.serverSocketOut.isClosed()) {
                    this.serverSocketOut.close();
                }
                if (this.clientSocket != null && !this.clientSocket.isClosed()) {
                    this.clientSocket.close();
                }
            } catch (IOException closeEx) {
                // TODO Auto-generated catch block
                LOG.severe("IO Exception while cleanup: " + closeEx.toString());
            }

        }

        synchronized void enqueueCall(String message) throws LSPException {
            if (process == null || !process.isAlive() || inWriter == null) {
                LOG.warning(this.lang + "LSP is down");
                throw new LSPException();
            }
            inWriter.write(message);
            inWriter.flush();
        }

        void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC socket, int in, int out) {
            this.ipc = socket;
            this.socketIn = in;
            this.socketOut = out;
            LOG.info(String.format("Socket IPC: in %d out %d", this.socketIn, this.socketOut));
        }

        void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC namedpipes, String in, String out) {
            this.ipc = namedpipes;
            this.pipeIn = in;
            this.pipeOut = out;
            LOG.info(String.format("Named pipe IPC: in %s out %s", this.pipeIn, this.pipeOut));

        }

        void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC clientsocket, int port) {
            this.ipc = clientsocket;
            this.clientSocketPort = port;
            LOG.info(String.format("Client socket IPC: %d", this.clientSocketPort));
        }

        void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC stream) {
            this.ipc = stream;
            LOG.info("Stream IPC");
        }

        String getProjPath() {
            return this.projPath;
        }

    }

    private Map<String, LangServerCtx> langContexts;

    LSPProcessManager(Map<String, LangServerCtx> langContexts) {
        this.langContexts = langContexts;
    }

    private Map<String, LSPProcess> lspProcesses = Collections.synchronizedMap(new HashMap<String, LSPProcess>());

    synchronized LSPProcess createProcess(String wsKey, String lang, RemoteEndpoint.Basic remoteEndpoint, String ownerSessionId) throws LSPException {

        String procKey = processKey(wsKey, lang);
        String rpcType = langContexts.get(lang).getRpcType();
        String wsKeyElem[] = wsKey.split(WS_KEY_DELIMITER, 3);

        disconnect(lang, ownerSessionId);
        LSPProcess lspProcess = new LSPProcess(wsKeyElem, lang, langContexts.get(lang).getProcessBuilder(wsKeyElem), remoteEndpoint, ownerSessionId);
        switch (rpcType) {
            case ENV_IPC_SOCKET:
                socketEnv(lspProcess, LangServerCtx.LangPrefix(lang));
                break;
            case ENV_IPC_PIPES:
                pipeEnv(lspProcess, LangServerCtx.LangPrefix(lang));
                break;
            case ENV_IPC_CLIENT:
                clientSocketEnv(lspProcess, LangServerCtx.LangPrefix(lang));
                break;
            default:
                streamEnv(lspProcess);
        }
        lspProcesses.put(procKey, lspProcess);
        return lspProcess;
    }

    synchronized void cleanProcess(String ws, String lang, String ownerSessionId) {
        String procKey = LSPProcessManager.processKey(ws, lang);
        LSPProcess lspProc = lspProcesses.get(procKey);
        if (lspProc != null && lspProc.getOwnerSessionId().equals(ownerSessionId)) {
            lspProc = lspProcesses.remove(procKey);
            if (lspProc != null) lspProc.cleanup();
        }
    }

    @SuppressWarnings("unchecked")
    void disconnect(String lang, String sessionOwnerId) {
        LOG.info("LSP Manager disconnect for session " + sessionOwnerId);
        Optional<Entry<String, LSPProcess>> optLspProc = lspProcesses.entrySet().stream().filter(p -> p.getValue().getLang().equals(lang)).findFirst();


        if (optLspProc.isPresent() && !optLspProc.get().getValue().getOwnerSessionId().equals(sessionOwnerId)) {
            LOG.info("LSP Manager disconnect from " + optLspProc.get().getValue().getOwnerSessionId());
            LSPProcess lsp = lspProcesses.remove(optLspProc.get().getKey());
            if (lsp != null) lsp.cleanup();
        }
    }

    static String processKey(String ws, String lang) {
        return ws + ":" + lang;
    }

    LSPProcess getProcess(String processKey) {
        return lspProcesses.get(processKey);
    }

    private void pipeEnv(LSPProcess newLsp, String prefix) {
        newLsp.confIpc(LSPProcess.IPC.NAMEDPIPES, System.getenv(prefix + ENV_PIPE_IN), System.getenv(prefix + ENV_IPC_OUT_PORT));
    }

    private void socketEnv(LSPProcess newLsp, String prefix) {
        try {
            newLsp.confIpc(LSPProcess.IPC.SOCKET, Integer.parseInt(System.getenv(prefix + ENV_IPC_IN_PORT)), Integer.parseInt(System.getenv(prefix + ENV_IPC_OUT_PORT)));
        } catch (NumberFormatException fe) {
            throw new LSPConfigurationException();
        }
    }

    private void clientSocketEnv(LSPProcess newLsp, String prefix) {
        try {
            newLsp.confIpc(LSPProcess.IPC.CLIENTSOCKET, Integer.parseInt(System.getenv(prefix + ENV_IPC_CLIENT_PORT)));
        } catch (NumberFormatException fe) {
            throw new LSPConfigurationException();
        }
    }

    private void streamEnv(LSPProcess newLsp) {
        newLsp.confIpc(LSPProcess.IPC.STREAM);
    }
}
