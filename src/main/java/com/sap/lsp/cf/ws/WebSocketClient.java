package com.sap.lsp.cf.ws;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

@ClientEndpoint
public class WebSocketClient {

    private Session userSession = null;
    private CompletableFuture<String> response;
    private String waitFor;

    public WebSocketClient(String uri) {
        try {
            WebSocketContainer container = ContainerProvider
                .getWebSocketContainer();
            container.connectToServer(this, new URI(uri));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean isClosed() {
        return userSession == null || !userSession.isOpen();
    }

    @OnOpen
    public void onOpen(Session userSession) {
        this.userSession = userSession;
    }

    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        this.userSession = null;
    }

    @OnMessage
    public void onMessage(String message) {
    	if ( message.equals(waitFor)) {
    		this.response.complete(message);
    	}
    }

    public CompletableFuture<String> sendRequest(String message, String response) throws RuntimeException {
    	this.response = new CompletableFuture<String>(); 
    	waitFor = response;
    	if (userSession != null && userSession.isOpen())
            try {
                this.userSession.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        else {
            throw new RuntimeException("Session closed");
        }
    	return this.response;
    }
    
    public void sendNotification(String message) throws RuntimeException {
    	if (userSession != null && userSession.isOpen())
            try {
                this.userSession.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        else {
            throw new RuntimeException("Session closed");
        }
    	
    }

}
