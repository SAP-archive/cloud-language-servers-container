package com.sap.lsp.cf.ws;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class WSChangeObserver {
	
	private static final Logger LOG = Logger.getLogger(WSChangeObserver.class.getName());
	
	static class LSPDestination {
		private WebSocketClient client;
		private static String LSP_HOST = "ws://localhost:8080/LanguageServer";

		/**
		 * LSP destination constructor - also initialize connection to LSP end Point
		 * @param path String
		 * @param webSocketClient WebSocketClient
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
	
	private Map<String, LSPDestination> lspDestinations; // Map of registered LSP url's
	private ChangeType changeType;
	private Map<String,LSPDestination> artifacts; // Map artifact -> url
	
	public enum ChangeType { CHANGE_CREATED(1), CHANGE_UPDATED(2), CHANGE_DELETED(3);
		private int opcode;
		ChangeType(int opcode) { this.opcode = opcode; }
		public int opcode() { return this.opcode; }
		
	};
	
	/**
	 * @param ct ChangeType according LSP
	 * @param destinations Map path -> LSP destination
	 */
	WSChangeObserver(ChangeType ct, Map<String, LSPDestination> destinations) {
		lspDestinations = destinations;
		this.changeType = ct;
		artifacts = new HashMap<>();
		
	}
	
	/**
	 * Registers artifact and maps to destination if corresponding LSP destination is listening 
	 */
	void onChangeReported(String artifactRelPath, String saveDir) {
		String wsKey ="ws" + File.separator + artifactRelPath;
		String artifactUrl = saveDir + artifactRelPath;
		LOG.info(String.format("WS Sync Observer ws key %s artifact %s", wsKey, artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1)));
		lspDestinations.entrySet().stream()
					.filter(map -> artifactFilter(map, wsKey))
					.forEach((e) -> { artifacts.put(artifactUrl, e.getValue()); });
	}
	
	/**
	 * All destinations to notify something
	 */
	Collection<LSPDestination> getLSPDestinations() {
		return artifacts.values();
	}
	
	/**
	 * All artifacts to notify as watched per destination
	 */
	List<String> getArtifacts(LSPDestination forDest) {
		return artifacts.entrySet().stream().filter(map -> forDest == map.getValue())
			.map(Entry::getKey).collect(Collectors.toList());
	}


	int getType() {
		return changeType.opcode();
	}
	
	private static boolean artifactFilter(Map.Entry<String, LSPDestination> regEntry, String path) {
		String[] regKey = regEntry.getKey().split(":");
		String pathFilter = "ws" + regKey[0];
		return path.startsWith(pathFilter);
	}

}
