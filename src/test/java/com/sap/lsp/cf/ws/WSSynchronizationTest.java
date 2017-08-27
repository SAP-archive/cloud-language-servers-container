package com.sap.lsp.cf.ws;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.easymock.PowerMock;
import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.replayAll;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.sap.lsp.cf.ws.WSChangeObserver.LSPDestination;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ WSSynchronization.class, WebSocketClient.class })

public class WSSynchronizationTest {
	
	private static final Logger LOG = Logger.getLogger(WSSynchronizationTest.class.getName());

	private static final String SERVLET_PATH = "http://localhost:8000/WSSynchronization";

	private static final String ARTIFACT_PATH = "/myProject/myModule/java/test.java";

	private static final String NEW_ARTIFACT_PATH = "/newProject/newModule/java1/test1.java";

	private static int NO_CONTENT_STATS = 1;
	private static int CREATED_CALLS = 1;
	private static int OK_STATS = 1;

	static WebSocketClient wsClient = Mockito.mock(WebSocketClient.class);

	private static WSSynchronization cut = new WSSynchronization();

	private static LSPEndPointTestUtil testUtil;

	private static HttpServletResponse response;

	private static String wd;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		// wsClient = Mockito.mock(WebSocketClient.class);
		// PowerMock.mockStatic(WebSocketClient.class);
		Mockito.doNothing().when(wsClient).connect(any());
		
		testUtil = new LSPEndPointTestUtil();
		String log = testUtil.createInfra();
		LOG.info(log);
		wd = testUtil.getWdPath().toString();
		cut.setSaveDir(wd);
		cut.init();
		
		response = Mockito.spy(HttpServletResponse.class);
		PrintWriter responseWriter = Mockito.mock(PrintWriter.class);
		Mockito.when(responseWriter.append(any(String.class))).thenReturn(responseWriter);
		Mockito.when(response.getWriter()).thenReturn(responseWriter);
	
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}
	
	@After
	public void cleanUp() throws Exception {
		// cut.cleanUpWS(Paths.get(new File(wd).getPath()));
	}

	@Test
	public void testWSSynchronization() throws ServletException {
		cut.init();
	}

	@Test
	public void testDoGet() throws FileNotFoundException, IOException, ServletException {
		HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
		Part part1 = Mockito.mock(Part.class);
		Mockito.when(part1.getInputStream()).thenReturn( getZipStream("putTest.zip") );
		Collection<Part> parts = Collections.singleton(part1);
		Mockito.when(requestInit.getParts()).thenReturn(parts);
		
		String workspaceSaveDir = testUtil.getWdPath().toString();
		cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);
		CREATED_CALLS++;
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH);
		
		cut.doGet(request, response);
		Mockito.verify(response, times(OK_STATS)).setStatus(HttpServletResponse.SC_OK);
		OK_STATS++;

	}

	@Test
	public void testDoGetSyncLost() throws FileNotFoundException, IOException, ServletException {
		cut.cleanUpWS(Paths.get(new File(wd).getPath()));
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH);

		cut.doGet(request, response);
		
		Mockito.verify(response,times(NO_CONTENT_STATS)).setStatus(HttpServletResponse.SC_NO_CONTENT);
		NO_CONTENT_STATS++;

	}
	
	@Test
	public void testDoPutInit() throws IOException, ServletException {
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH);
		Part part1 = Mockito.mock(Part.class);

		Mockito.when(part1.getInputStream()).thenReturn( getZipStream("putTest.zip") );
		Collection<Part> parts = Collections.singleton(part1);
		Mockito.when(request.getParts()).thenReturn(parts);
		

		cut.doPut(request, response);
		Mockito.verify(response, times(CREATED_CALLS)).setStatus(HttpServletResponse.SC_CREATED);
		CREATED_CALLS++;
		assertTrue("File create ", new File(wd + File.separator + "myProject"
												+ File.separator + "myModule"
												+ File.separator + "java"
												+ File.separator + "test.java").exists());
		assertTrue("File create ", new File(wd + File.separator + ".sync").exists());
	}
	
	@Test
	public void testNewProject() throws IOException, ServletException {
		HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
		Part part1 = Mockito.mock(Part.class);
		Mockito.when(part1.getInputStream()).thenReturn( getZipStream("putTest.zip") );
		Collection<Part> parts = Collections.singleton(part1);
		Mockito.when(requestInit.getParts()).thenReturn(parts);
		
		String workspaceSaveDir = testUtil.getWdPath().toString();
		cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);
		CREATED_CALLS++;
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + NEW_ARTIFACT_PATH);
		part1 = Mockito.mock(Part.class);

		Mockito.when(part1.getInputStream()).thenReturn( getZipStream("newProject.zip") );
		parts = Collections.singleton(part1);
		Mockito.when(request.getParts()).thenReturn(parts);
		

		cut.doPut(request, response);
		Mockito.verify(response, times(CREATED_CALLS)).setStatus(HttpServletResponse.SC_CREATED);
		CREATED_CALLS++;
		//'/newProject/newModule/java1/test1.java'
		assertTrue("File create ", new File(wd + File.separator + "newProject"
												+ File.separator + "newModule"
												+ File.separator + "java1"
												+ File.separator + "test1.java").exists());
	}
	
	@Test
	public void testDoPutSyncLost() throws IOException, ServletException {
		cut.cleanUpWS(Paths.get(new File(wd).getPath()));
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + NEW_ARTIFACT_PATH);
		Part part1 = Mockito.mock(Part.class);

		Mockito.when(part1.getInputStream()).thenReturn( getZipStream("newProject.zip") );
		Collection<Part>parts = Collections.singleton(part1);
		Mockito.when(request.getParts()).thenReturn(parts);
		

		cut.doPut(request, response);

		Mockito.verify(response,times(NO_CONTENT_STATS)).setStatus(HttpServletResponse.SC_NO_CONTENT);
		NO_CONTENT_STATS++;
		
	}
	

	@Test
	public void testDoPost() throws IOException, ServletException {
		HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
		Part part1 = Mockito.mock(Part.class);
		Mockito.when(part1.getInputStream()).thenReturn( getZipStream("putTest.zip") );
		Collection<Part> parts = Collections.singleton(part1);
		Mockito.when(requestInit.getParts()).thenReturn(parts);
		
		String workspaceSaveDir = testUtil.getWdPath().toString();
		cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);
		CREATED_CALLS++;
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
		part1 = Mockito.mock(Part.class);
		Mockito.when(part1.getInputStream()).thenReturn(getZipStream("postTest.zip") );
		parts = Collections.singleton(part1);
		Mockito.when(request.getParts()).thenReturn(parts);

		cut.doPost(request, response);
		Mockito.verify(response, times(OK_STATS)).setStatus(HttpServletResponse.SC_OK);
		OK_STATS++;
		assertTrue("File create ", new File(wd + File.separator + "myProject"
												+ File.separator + "myModule"
												+ File.separator + "java"
												+ File.separator + "test.java").exists());
		
	}
	
	@Test
	public void testDoPostSyncLost() throws IOException, ServletException {
		cut.cleanUpWS(Paths.get(new File(wd).getPath()));
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
		Part part1 = Mockito.mock(Part.class);
		Mockito.when(part1.getInputStream()).thenReturn(getZipStream("postTest.zip") );
		Collection<Part> parts = Collections.singleton(part1);
		Mockito.when(request.getParts()).thenReturn(parts);

		cut.doPost(request, response);

		Mockito.verify(response,times(NO_CONTENT_STATS)).setStatus(HttpServletResponse.SC_NO_CONTENT);
		NO_CONTENT_STATS++;
		
	}
	

	@Test
	public void testDoDelete() throws IOException, ServletException {
		HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
		Part part1 = Mockito.mock(Part.class);
		Mockito.when(part1.getInputStream()).thenReturn( getZipStream("putTest.zip") );
		Collection<Part> parts = Collections.singleton(part1);
		Mockito.when(requestInit.getParts()).thenReturn(parts);
		
		String workspaceSaveDir = testUtil.getWdPath().toString();
		cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);
		CREATED_CALLS++;
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);

		cut.doDelete(request, response);
		Mockito.verify(response,times(OK_STATS)).setStatus(HttpServletResponse.SC_OK);
		OK_STATS++;
		assertFalse("File create ", new File(wd + File.separator + "myProject"
												+ File.separator + "myModule"
												+ File.separator + "java"
												+ File.separator + "test.java").exists());
		

	}
	
	@Test
	public void testDoDeleteSyncLost() throws IOException, ServletException {
		cut.cleanUpWS(Paths.get(new File(wd).getPath()));
		
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
		Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);

		cut.doDelete(request, response);
		Mockito.verify(response,times(NO_CONTENT_STATS)).setStatus(HttpServletResponse.SC_NO_CONTENT);
		NO_CONTENT_STATS++;
		
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
	
	private FileInputStream getZipStream(String testData ) throws FileNotFoundException {

		assert(new File(System.getProperty("user.dir") + File.separator + "src/test/javascript/resources/" +testData).exists());
		return new FileInputStream(System.getProperty("user.dir") + File.separator + "src/test/javascript/resources/" + testData);

	}

}
