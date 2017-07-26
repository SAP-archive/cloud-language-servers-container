package com.sap.lsp.cf.ws;

import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

public class GetHttpSessionConfigurator extends ServerEndpointConfig.Configurator {
    
    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        config.getUserProperties().put(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL,request.getHeaders().get(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL));
    }

}