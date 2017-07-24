package com.sap.lsp.cf.ws;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCode;
import javax.websocket.EndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;

import org.powermock.api.easymock.PowerMock;
import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


@RunWith(PowerMockRunner.class)
@PrepareForTest( { LangServerCtx.class, LanguageServerWSEndPoint.class } )



@Ignore
public class LanguageServerWSEndPointTest {
	
	private static final Logger LOG = Logger.getLogger(LanguageServerWSEndPointTest.class.getName());
	
	static LanguageServerWSEndPoint cut = null;
	
	@Mock
	static Session testSession = null;
	
	@Mock
	static EndpointConfig endpointConfig;
	
	@Mock
	static RemoteEndpoint.Basic remoteEndpointMock;
	
	static LSPProcessManager procManagerMock = null;
	
	@Mock
	static LSPProcessManager.LSPProcess lspProcessMock;
	
	static String TEST_MESSAGE =  "Content-Length: 113\r\n\r\n" +
			"{\r\n" +
			"\"jsonrpc\": \"2.0\",\r\n" +
			"\"id\" : \"2\",\r\n" +
			"\"method\" : \"workspace/symbol\",\r\n" +
			"\"params\" : {\r\n" +
			"\"query\": \"ProductService*\"\r\n" +
			"}\r\n}";
	
	static String READY_MESSAGE = "Content-Length: 45\r\n\r\n" +
			"{\"jsonrpc\": \"2.0\",\"method\": \"protocol/Ready\"}";
	
	static String readyMessage;
	static String testMessage;
	static boolean cleanUpCall;

	private static LSPEndPointTestUtil testUtil;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		
		testUtil = new LSPEndPointTestUtil();
		String log = testUtil.createInfra();
		LOG.info(log);
		testUtil.MockServerContext();		
		
		Map<String, List<String>> reqParam = new HashMap<String,List<String>>();
		testSession = Mockito.mock(Session.class);
		Mockito.when(testSession.getRequestParameterMap()).thenReturn(reqParam);
		Mockito.when(testSession.getId()).thenReturn("0");
		
		remoteEndpointMock = Mockito.mock(RemoteEndpoint.Basic.class);
		
		remoteEndpointMock = Mockito.mock(RemoteEndpoint.Basic.class); 
		Mockito.doAnswer(new Answer<Void>() {
		      public Void answer(InvocationOnMock invocation) {
		          Object readyMsg = invocation.getArguments()[0];
		          readyMessage = (String)readyMsg;
		          return null;
		      }})
		  .when(remoteEndpointMock).sendText(any());

		Mockito.when(testSession.getBasicRemote()).thenReturn(remoteEndpointMock);
		
		endpointConfig = Mockito.mock(EndpointConfig.class);
		
		procManagerMock = Mockito.mock(LSPProcessManager.class);
		lspProcessMock = Mockito.mock(LSPProcess.class);
		doReturn(lspProcessMock).when(procManagerMock).createProcess(any(), any(), any());
		doReturn(lspProcessMock).when(procManagerMock).getProcess(any());
		
		// Mock lspProc.enqueueCall(message)
		Mockito.doAnswer(new Answer<Void>() {
		      public Void answer(InvocationOnMock invocation) {
		          Object testMsg = invocation.getArguments()[0];
		          testMessage = (String)testMsg;
		          return null;
		      }})
		  .when(lspProcessMock).enqueueCall(any());
		
		// nClose() -> procManager.cleanProcess
		Mockito.doAnswer(new Answer<Void>() {
		      public Void answer(InvocationOnMock invocation) {
		    	  cleanUpCall = true;
		          return null;
		      }})
		  .when(procManagerMock).cleanProcess(any(), any());
		


		cut = new LanguageServerWSEndPoint();
		setInternalState(cut, "langContexts", testUtil.getCtx());
		setInternalState(cut, "procManager", procManagerMock);

		
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		
		//PowerMockito.
	}

	@Test
	public void testLanguageServerWSEndPoint() {
		//fail("Not yet implemented");
	}

	@Test
	public void testOnOpen() {
		cut.onOpen("testWS", "aLang", testSession, endpointConfig);
		assertEquals(READY_MESSAGE, readyMessage);
	}

	@Test
	public void testOnMessage() {
		cut.onMessage("testWS", "aLang", TEST_MESSAGE, testSession );
		assertEquals(TEST_MESSAGE, testMessage);
	}

	@Test
	public void testOnClose() {
		cleanUpCall = false;
		cut.onClose("testWS", "aLang", testSession, new CloseReason(CloseReason.CloseCodes.NO_STATUS_CODE,"test"));
		assertTrue(cleanUpCall);
	}

	@Test
	public void testOnError() {
		cut.onError(testSession, new LSPException());
	}
	
	// ------------------------------------------------------------------------------------------------
	public static void setInternalState(Object target, String field, Object value) {
	    Class<?> c = target.getClass();
	    try {
	    	Field f = c.getDeclaredField(field);
	        f.setAccessible(true);
	        f.set(target, value);
	    } catch (Exception e) {
	        throw new RuntimeException(
	            "Unable to set internal state on a private field. [...]", e);
	    }
	}

}
