package com.sap.lsp.cf.ws;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

class WSChangeObserver {
    private static final Logger LOG = Logger.getLogger(WSChangeObserver.class.getName());
    private Map<String, LSPDestination> lspDestinations; // Map of registered LSP url's
    private Map<LSPDestination, List<String>> destinations2Artifacts; // Map artifact -> url
    private ChangeType changeType;


    static class LSPDestination {
        private WebSocketClient client;
        private static String LSP_HOST = "ws://localhost:8080/LanguageServer";

        /**
         * LSP destination constructor - also initialize connection to LSP end Point
         */
        LSPDestination(String path, WebSocketClient webSocketClient) {
            client = webSocketClient;
            LOG.info("Observer establishing connection to LSP destination " + LSP_HOST + path + "?local");
            client.connect(LSP_HOST + path + "?local");
        }

        WebSocketClient getWebSocketClient() {
            return client;
        }
    }

    public enum ChangeType {
        CHANGE_CREATED(1), CHANGE_UPDATED(2), CHANGE_DELETED(3);
        private int opcode;

        ChangeType(int opcode) {
            this.opcode = opcode;
        }

        public int opcode() {
            return this.opcode;
        }
    }

    /**
     * @param ct           ChangeType according LSP
     * @param destinations Map path -> LSP destination
     */
    WSChangeObserver(ChangeType ct, Map<String, LSPDestination> destinations) {
        lspDestinations = destinations;
        this.changeType = ct;
        destinations2Artifacts = new HashMap<>();
    }

    private String buildLspNotification(int type, List<String> artifacts) {
        JsonArrayBuilder changes = Json.createArrayBuilder();
        for (String sUrl : artifacts) {
            changes.add(Json.createObjectBuilder().add("uri", "file://" + sUrl).add("type", type).build());
        }
        JsonObject bodyObj = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "workspace/didChangeWatchedFiles")
                .add("params", Json.createObjectBuilder()
                        .add("changes", changes.build())
                        .build())
                .build();
        String body = bodyObj.toString();
        return String.format("Content-Length: %d\r\n\r\n%s", body.length(), body);
    }

    void notifyLSP() {
        for (LSPDestination dest : destinations2Artifacts.keySet()) {
            String message = buildLspNotification(changeType.opcode(), destinations2Artifacts.get(dest));
            dest.getWebSocketClient().sendNotification(message);
        }
    }

    /**
     * Registers artifact and maps to destination if corresponding LSP destination is listening
     */
    void onChangeReported(String artifactRelPath, String saveDir) {
        String wsKey = "ws/" + artifactRelPath;
        String artifactUrl = saveDir + artifactRelPath;
        LOG.info(String.format("WS Sync Observer ws key %s artifact %s", wsKey, artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1)));
        lspDestinations.entrySet().stream()
                .filter(entry -> artifactFilter(entry, wsKey))
                .forEach(entry -> {
                    final List<String> artifacts = this.destinations2Artifacts.computeIfAbsent(entry.getValue(), dest -> new ArrayList<>());
                    artifacts.add(artifactUrl);
                });
    }


    private static boolean artifactFilter(Map.Entry<String, LSPDestination> regEntry, String path) {
        String[] regKey = regEntry.getKey().split(":");
        String pathFilter = "ws" + regKey[0];
        return path.startsWith(pathFilter);
    }

}
