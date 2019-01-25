package com.sap.lsp.cf.ws;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class LanguageServerWSEndPointTest {

    private static final String wsSynchronizationUrl = "/WSSynchronization";
    private static String READY_MESSAGE = "Content-Length: 45\r\n\r\n"
        + "{\"jsonrpc\": \"2.0\",\"method\": \"protocol/Ready\"}";

    private static LanguageServerWSEndPoint languageServerWSEndPoint = new LanguageServerWSEndPoint();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8080);

    @Mock
    private Session testSession;
    @Mock
    private EndpointConfig endpointConfig;
    @Mock
    private RemoteEndpoint.Basic remoteEndpoint;

    private static LSPProcessManager lspProcessManager;
    private static LSPProcess lspProcess;

    @BeforeClass
    public static void setupBeforeClass() throws LSPException {
        lspProcessManager = mock(LSPProcessManager.class);
        lspProcess = mock(LSPProcess.class);
        doReturn(lspProcess).when(lspProcessManager).createProcess(any(), any(), any(), any());
        doReturn(lspProcess).when(lspProcessManager).getProcess(any());
        LanguageServerWSEndPoint.setTestContext(lspProcessManager);

        System.setProperty("com.sap.lsp.cf.ws.token", "12345");
        System.setProperty("com.sap.lsp.cf.ws.expirationDate",
                Long.toString(System.currentTimeMillis() + 60 * 60 * 1000));
    }

    @Before
    public void setup() {
        when(testSession.getNegotiatedSubprotocol()).thenReturn("access_token");
        Map<String, Object> reqProtocolMap = Collections.singletonMap(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL,
                Collections.singletonList("access_token,12345"));
        when(endpointConfig.getUserProperties()).thenReturn(reqProtocolMap);
        when(testSession.getBasicRemote()).thenReturn(remoteEndpoint);
    }
    
    @Test
    public void testOnOpen() throws IOException {
        prepareLspRegPostStub(true);
        doReturn("/").when(lspProcess).getProjPath();
        
        languageServerWSEndPoint.onOpen("testWS", "aLang", testSession, endpointConfig);
        
        verify(remoteEndpoint).sendText(READY_MESSAGE);
        assertRequestBody("/:aLang=/testWS/aLang");
    }

    @Test
    public void testOnOpenP() throws IOException {
        prepareLspRegPostStub(true);
        doReturn("/myProj/").when(lspProcess).getProjPath();
        
        languageServerWSEndPoint.onOpen("testWS~myProj", "aLang", testSession, endpointConfig);
        
        verify(remoteEndpoint).sendText(READY_MESSAGE);
        assertRequestBody("/myProj/:aLang=/testWS~myProj/aLang");
    }

    @Test
    public void testIllegalWorkspace() throws IOException {
        languageServerWSEndPoint.onOpen("testWS&~myProj", "aLang", testSession, endpointConfig);
        
        verify(testSession, times(1)).close(any());
    }

    @Test
    public void testOnOpenM() throws IOException {
        prepareLspRegPostStub(true);
        doReturn("/myProj/myModule/").when(lspProcess).getProjPath();
        
        languageServerWSEndPoint.onOpen("testWS~myProj~myModule", "aLang", testSession, endpointConfig);
        
        verify(remoteEndpoint).sendText(READY_MESSAGE);
    }

    @Test
    public void testOnMessage() throws LSPException {
        String testMessage = "Content-Length: 113\r\n\r\n" + "{\r\n" + "\"jsonrpc\": \"2.0\",\r\n"
                + "\"id\" : \"2\",\r\n" + "\"method\" : \"workspace/symbol\",\r\n" + "\"params\" : {\r\n"
                + "\"query\": \"ProductService*\"\r\n" + "}\r\n}";
        
        languageServerWSEndPoint.onMessage("testWS", "aLang", testMessage, testSession);
        
        verify(lspProcess).enqueueCall(testMessage);
    }

    @Test
    public void testOnClose() {
        prepareLspRegPostStub(false);
        
        languageServerWSEndPoint.onClose("testWS", "aLang", testSession,
                new CloseReason(CloseReason.CloseCodes.NO_STATUS_CODE, "test"));
        
        verify(lspProcessManager).cleanProcess(any(), any(), any());
    }

    @Test
    public void testOnError() {
        languageServerWSEndPoint.onError(testSession, new LSPException());
    }

    @Test
    public void onOpenLsp_timeout() throws IOException {
        prepareLspRegPostStub(true);
        Map<String, List<String>> reqParam = new HashMap<>();
        reqParam.put("lsp_timeout", Arrays.asList("100"));
        when(testSession.getRequestParameterMap()).thenReturn(reqParam);
        when(testSession.getId()).thenReturn("0");
        doReturn("/").when(lspProcess).getProjPath();
        
        languageServerWSEndPoint.onOpen("testWS", "aLang", testSession, endpointConfig);

        verify(testSession, times(1)).setMaxIdleTimeout(100L);
        verify(remoteEndpoint).sendText(READY_MESSAGE);
        assertRequestBody("/:aLang=/testWS/aLang");
    }

    private void prepareLspRegPostStub(Boolean register) {
        stubFor(
            post(urlEqualTo(wsSynchronizationUrl))
                .withHeader("Register-lsp", equalTo(register.toString()))
                .willReturn(aResponse().withStatus(200)));
    }

    private void assertRequestBody(String expectedRequestBody) throws UnsupportedEncodingException {
        List<ServeEvent> allServeEvents = getAllServeEvents();
        String encodedRequestBody = allServeEvents.get(0).getRequest().getBodyAsString();
        String decodedRequeatBody = URLDecoder.decode(encodedRequestBody, StandardCharsets.UTF_8.toString());
        assertEquals(expectedRequestBody, decodedRequeatBody);
    }

}
