package com.sap.lsp.cf.ws;

import com.sap.lsp.cf.ws.WSChangeObserver.ChangeType;
import com.sap.lsp.cf.ws.WSChangeObserver.LSPDestination;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;

public class WSChangeObserverTest {
    @Test
    public void notifySingleDestination() {
        final Map<String, WSChangeObserver.LSPDestination> destinations = new HashMap<>();
        final WebSocketClient wsClient = Mockito.mock(WebSocketClient.class);
        destinations.put(File.separator + "myPath:lang1", new LSPDestination("myPath", wsClient));
        final WSChangeObserver wsChangeObserver = new WSChangeObserver(ChangeType.CHANGE_CREATED, destinations);
        wsChangeObserver.onChangeReported("myPath/a/b/c", "home/");
        wsChangeObserver.notifyLSP();
        Mockito.verify(wsClient).sendNotification(contains("\"method\":\"workspace/didChangeWatchedFiles\",\"params\":{\"changes\":[{\"uri\":\"file://home/myPath/a/b/c\",\"type\":1"));
    }

    @Test
    public void notifyMultipleChanges() {
        final Map<String, WSChangeObserver.LSPDestination> destinations = new HashMap<>();
        final WebSocketClient wsClient = Mockito.mock(WebSocketClient.class);
        destinations.put(File.separator + "myPath:lang1", new LSPDestination("myPath", wsClient));
        final WSChangeObserver wsChangeObserver = new WSChangeObserver(ChangeType.CHANGE_CREATED, destinations);
        wsChangeObserver.onChangeReported("myPath/a/b/c", "home/");
        wsChangeObserver.onChangeReported("myPath/d/e/f", "home/");
        wsChangeObserver.notifyLSP();
        Mockito.verify(wsClient).sendNotification(contains("\"method\":\"workspace/didChangeWatchedFiles\",\"params\":{\"changes\":[{\"uri\":\"file://home/myPath/a/b/c\",\"type\":1},{\"uri\":\"file://home/myPath/d/e/f\",\"type\":1}]"));
    }

    @Test
    public void notifyWhenMultiDestinationsSamePath() {
        final Map<String, WSChangeObserver.LSPDestination> destinations = new HashMap<>();
        final WebSocketClient wsClient1 = Mockito.mock(WebSocketClient.class);
        final WebSocketClient wsClient2 = Mockito.mock(WebSocketClient.class);
        destinations.put(File.separator + "myPath:lang1", new LSPDestination("myPath", wsClient1));
        destinations.put(File.separator + "myPath:lang2", new LSPDestination("myPath", wsClient2));
        final WSChangeObserver wsChangeObserver = new WSChangeObserver(ChangeType.CHANGE_CREATED, destinations);
        wsChangeObserver.onChangeReported("myPath/a/b/c", "home/");
        wsChangeObserver.notifyLSP();
        final String expectedSubstring = "\"method\":\"workspace/didChangeWatchedFiles\",\"params\":{\"changes\":[{\"uri\":\"file://home/myPath/a/b/c\",\"type\":1";
        Mockito.verify(wsClient1).sendNotification(contains(expectedSubstring));
        Mockito.verify(wsClient2).sendNotification(contains(expectedSubstring));
    }

    @Test
    public void notifyWhenMultiDestinationsDifferentPath() {
        final Map<String, WSChangeObserver.LSPDestination> destinations = new HashMap<>();
        final WebSocketClient wsClient1 = Mockito.mock(WebSocketClient.class);
        final WebSocketClient wsClient2 = Mockito.mock(WebSocketClient.class);
        destinations.put(File.separator + "myPath1:lang1", new LSPDestination("myPath", wsClient1));
        destinations.put(File.separator + "myPath2:lang2", new LSPDestination("myPath", wsClient2));
        final WSChangeObserver wsChangeObserver = new WSChangeObserver(ChangeType.CHANGE_CREATED, destinations);
        wsChangeObserver.onChangeReported("myPath1/a/b/c", "home/");
        wsChangeObserver.notifyLSP();
        final String expectedSubstring = "\"method\":\"workspace/didChangeWatchedFiles\",\"params\":{\"changes\":[{\"uri\":\"file://home/myPath1/a/b/c\",\"type\":1";
        Mockito.verify(wsClient1).sendNotification(contains(expectedSubstring));
        Mockito.verify(wsClient2, never()).sendNotification(any());
    }


}
