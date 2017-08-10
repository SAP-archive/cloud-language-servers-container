package com.sap.lsp.cf.ws;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClients;
import org.easymock.Mock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.DoesNothing;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.easymock.PowerMock;
import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.sap.lsp.cf.ws.WSChangeObserver.LSPDestination;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ WSSynchronization.class, WebSocketClient.class })

public class WSSynchronizationTest {

	static WebSocketClient wsClient = Mockito.mock(WebSocketClient.class);

	private static WSSynchronization cut = new WSSynchronization();

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cut.init();
		// wsClient = Mockito.mock(WebSocketClient.class);
		// PowerMock.mockStatic(WebSocketClient.class);
		Mockito.doNothing().when(wsClient).connect(any());
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testWSSynchronization() {
		// fail("Not yet implemented");
	}

	@Test
	public void testDoGet() {
		// fail("Not yet implemented");
	}

	@Test
	public void testDoPut() {
		// fail("Not yet implemented");
	}

	@Test
	public void testDoPost() {
		// fail("Not yet implemented");
	}

	@Test
	public void testDoRegisterLSPWS() throws IOException, ServletException {
		String regData = "/:aLang=/testWS/aLang";
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

		PowerMock.mockStatic(WebSocketClient.class);
		expect(WebSocketClient.getInstance()).andReturn(wsClient);
		replayAll();

		BufferedReader reader;
		// Register
		try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
			reader = new BufferedReader(new InputStreamReader(is));
		};
		Mockito.when(request.getReader()).thenReturn(reader);
		Mockito.when(request.getHeader("Register-lsp")).thenReturn("true");

		cut.doPost(request, response);
		Map<String, LSPDestination> reg = (Map<String, LSPDestination>) getInternalState(cut.getClass(), "lspDestPath");
		assertTrue("No listener registered", !reg.entrySet().isEmpty());
		assertNotNull("Wrong registration key", reg.get("/:aLang"));
		assertNotNull("Wrong registration url", reg.get("/:aLang").getWebSocketClient());

		// Unregister
		try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
			reader = new BufferedReader(new InputStreamReader(is));
		};
		Mockito.when(request.getReader()).thenReturn(reader);
		Mockito.when(request.getHeader("Register-lsp")).thenReturn("false");

		cut.doPost(request, response);
		reg = (Map<String, LSPDestination>) getInternalState(cut.getClass(), "lspDestPath");
		assertTrue("Listener unregistered", reg.entrySet().isEmpty());

	}

	@Test
	public void testDoRegisterLSPProj() throws IOException, ServletException {
		String regData = "/myProj/:aLang/=/testWS/aLang";
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

		PowerMock.mockStatic(WebSocketClient.class);
		expect(WebSocketClient.getInstance()).andReturn(wsClient);
		replayAll();

		BufferedReader reader;
		// Register
		try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
			reader = new BufferedReader(new InputStreamReader(is));
		};
		Mockito.when(request.getReader()).thenReturn(reader);
		Mockito.when(request.getHeader("Register-lsp")).thenReturn("true");

		cut.doPost(request, response);
		Map<String, LSPDestination> reg = (Map<String, LSPDestination>) getInternalState(cut.getClass(), "lspDestPath");
		assertTrue("No listener registered", !reg.entrySet().isEmpty());
		assertNotNull("Wrong registration key", reg.get("/myProj/:aLang/"));
		assertNotNull("Wrong registration url", reg.get("/myProj/:aLang/").getWebSocketClient());

		// Unregister
		try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
			reader = new BufferedReader(new InputStreamReader(is));
		};
		Mockito.when(request.getReader()).thenReturn(reader);
		Mockito.when(request.getHeader("Register-lsp")).thenReturn("false");
		cut.doPost(request, response);
		reg = (Map<String, LSPDestination>) getInternalState(cut.getClass(), "lspDestPath");
		assertTrue("Listener unregistered", reg.entrySet().isEmpty());

	}

	@Test
	public void testDoRegisterLSPModule() throws IOException, ServletException {
		String regData = "/myProj/myModule/:aLang/=/testWS/aLang";
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

		PowerMock.mockStatic(WebSocketClient.class);
		expect(WebSocketClient.getInstance()).andReturn(wsClient);
		replayAll();

		BufferedReader reader;
		// Register
		try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
			reader = new BufferedReader(new InputStreamReader(is));
		};
		Mockito.when(request.getReader()).thenReturn(reader);
		Mockito.when(request.getHeader("Register-lsp")).thenReturn("true");

		cut.doPost(request, response);
		Map<String, LSPDestination> reg = (Map<String, LSPDestination>) getInternalState(cut.getClass(), "lspDestPath");
		assertTrue("No listener registered", !reg.entrySet().isEmpty());
		assertNotNull("Wrong registration key", reg.get("/myProj/myModule/:aLang/"));
		assertNotNull("Wrong registration url", reg.get("/myProj/myModule/:aLang/").getWebSocketClient());

		// Unregister
		try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
			reader = new BufferedReader(new InputStreamReader(is));
		};
		Mockito.when(request.getReader()).thenReturn(reader);
		Mockito.when(request.getHeader("Register-lsp")).thenReturn("false");

		cut.doPost(request, response);
		reg = (Map<String, LSPDestination>) getInternalState(cut.getClass(), "lspDestPath");
		assertTrue("Listener unregistered", reg.entrySet().isEmpty());

	}

	@Test
	public void testDoDelete() {
		// fail("Not yet implemented");
	}

	// Utility methods
	// ------------------------------------------------------------------------------------
	public static Object getInternalState(Class<?> c, String field) {
		try {
			Field f = c.getDeclaredField(field);
			f.setAccessible(true);
			return f.get(cut);
		} catch (Exception e) {
			throw new RuntimeException("Unable to set internal state on a private field. [...]", e);
		}
	}

}
