package com.sap.lsp.cf.ws;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.powermock.api.easymock.PowerMock.replayAll;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.sap.lsp.cf.ws.WSChangeObserver.LSPDestination;

@RunWith(PowerMockRunner.class)
@PrepareForTest({WSSynchronization.class, WebSocketClient.class})

public class WSSynchronizationTest {

    private static final Logger LOG = Logger.getLogger(WSSynchronizationTest.class.getName());

    private static final String SERVLET_PATH = "http://localhost:8000/WSSynchronization";

    private static final String ARTIFACT_PATH = "/myProject/myModule/java/test.java";

    private static final String NEW_ARTIFACT_PATH = "/newProject/newModule/java1/test1.java";

    static WebSocketClient wsClient = Mockito.mock(WebSocketClient.class);

    private static WSSynchronization cut = new WSSynchronization();

    private static LSPEndPointTestUtil testUtil;

    private static HttpServletResponse response;

    private static String wd;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Mockito.doNothing().when(wsClient).connect(any());
        testUtil = new LSPEndPointTestUtil();
        String log = testUtil.createInfra();
        LOG.info(log);
        wd = testUtil.getWdPath().toString();
        cut.setSaveDir(wd);
        cut.init();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }
    
    @Before
    public void setup() throws IOException {
    	 response = Mockito.spy(HttpServletResponse.class);
         PrintWriter responseWriter = Mockito.mock(PrintWriter.class);
         Mockito.when(responseWriter.append(any(String.class))).thenReturn(responseWriter);
         Mockito.when(response.getWriter()).thenReturn(responseWriter);
    }

    @After
    public void tearDown() throws Exception {
        cut.cleanUpWS(Paths.get(new File(wd).getPath()));
    }

    @Test
    public void testWSSynchronization() throws ServletException {
        cut.init();
    }

    @Test
    public void testDoGet() throws FileNotFoundException, IOException, ServletException {
        HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
        Part part1 = Mockito.mock(Part.class);
        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part1);
        Mockito.when(requestInit.getParts()).thenReturn(parts);

        String workspaceSaveDir = testUtil.getWdPath().toString();
        cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
        Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH);

        cut.doGet(request, response);
        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void testDoGetSyncLost() throws FileNotFoundException, IOException, ServletException {
        cut.cleanUpWS(Paths.get(new File(wd).getPath()));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
        Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH);

        cut.doGet(request, response);

        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    public void testDoPutInit() throws IOException, ServletException {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
        Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH);
        Part part1 = Mockito.mock(Part.class);

        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part1);
        Mockito.when(request.getParts()).thenReturn(parts);

        cut.doPut(request, response);
        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_CREATED);
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
        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part1);
        Mockito.when(requestInit.getParts()).thenReturn(parts);

        String workspaceSaveDir = testUtil.getWdPath().toString();
        cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
        Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + NEW_ARTIFACT_PATH);
        part1 = Mockito.mock(Part.class);

        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("newProject.zip"));
        parts = Collections.singleton(part1);
        Mockito.when(request.getParts()).thenReturn(parts);

        cut.doPut(request, response);
        Mockito.verify(response, times(2)).setStatus(HttpServletResponse.SC_CREATED);
        //'/newProject/newModule/java1/test1.java'
        assertTrue("File create ", new File(wd + File.separator + "newProject"
                + File.separator + "newModule"
                + File.separator + "java1"
                + File.separator + "test1.java").exists());
    }
    
    @Test
    public void testNewProjectWhenZipContainsOnlyFullPathEntry() throws IOException, ServletException {
    	 HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
         Part part1 = Mockito.mock(Part.class);
         Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("putTest.zip"));
         Collection<Part> parts = Collections.singleton(part1);
         Mockito.when(requestInit.getParts()).thenReturn(parts);

         String workspaceSaveDir = testUtil.getWdPath().toString();
         cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);

         HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
         Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
         Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + NEW_ARTIFACT_PATH);
         part1 = Mockito.mock(Part.class);

         Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("newProjectWithOnlyFullPAthEntry.zip"));
         parts = Collections.singleton(part1);
         Mockito.when(request.getParts()).thenReturn(parts);

         cut.doPut(request, response);
         Mockito.verify(response, times(2)).setStatus(HttpServletResponse.SC_CREATED);
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

        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("newProject.zip"));
        Collection<Part> parts = Collections.singleton(part1);
        Mockito.when(request.getParts()).thenReturn(parts);

        cut.doPut(request, response);

        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }


    @Test
    public void testDoPost() throws IOException, ServletException {
        HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
        Part part1 = Mockito.mock(Part.class);
        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part1);
        Mockito.when(requestInit.getParts()).thenReturn(parts);

        String workspaceSaveDir = testUtil.getWdPath().toString();
        cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
        Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);
        part1 = Mockito.mock(Part.class);
        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("postTest.zip"));
        parts = Collections.singleton(part1);
        Mockito.when(request.getParts()).thenReturn(parts);

        cut.doPost(request, response);
        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);
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
        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("postTest.zip"));
        Collection<Part> parts = Collections.singleton(part1);
        Mockito.when(request.getParts()).thenReturn(parts);

        cut.doPost(request, response);

        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }


    @Test
    public void testDoDelete() throws IOException, ServletException {
        HttpServletRequest requestInit = Mockito.mock(HttpServletRequest.class);
        Part part1 = Mockito.mock(Part.class);
        Mockito.when(part1.getInputStream()).thenReturn(TestUtils.getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part1);
        Mockito.when(requestInit.getParts()).thenReturn(parts);

        String workspaceSaveDir = testUtil.getWdPath().toString();
        cut.initialSync(requestInit, response, "file://" + workspaceSaveDir, workspaceSaveDir);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
        Mockito.when(request.getServletPath()).thenReturn(SERVLET_PATH);

        cut.doDelete(request, response);
        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);
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
        Mockito.verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    public void testDoRegisterLspWS() throws IOException, ServletException {
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
        }
        Mockito.when(request.getReader()).thenReturn(reader);
        Mockito.when(request.getHeader("Register-lsp")).thenReturn("true");

        cut.doPost(request, response);
        Map<String, LSPDestination> reg = TestUtils.getInternalState(cut, "lspDestPath");
        assertTrue("No listener registered", !reg.entrySet().isEmpty());
        assertNotNull("Wrong registration key", reg.get("/:aLang"));
        assertNotNull("Wrong registration url", reg.get("/:aLang").getWebSocketClient());

        // Unregister
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        Mockito.when(request.getReader()).thenReturn(reader);
        Mockito.when(request.getHeader("Register-lsp")).thenReturn("false");

        cut.doPost(request, response);
        reg = TestUtils.getInternalState(cut, "lspDestPath");
        assertTrue("Listener unregistered", reg.entrySet().isEmpty());
    }

    @Test
    public void testDoRegisterLspProj() throws IOException, ServletException {
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
        }
        Mockito.when(request.getReader()).thenReturn(reader);
        Mockito.when(request.getHeader("Register-lsp")).thenReturn("true");

        cut.doPost(request, response);
        Map<String, LSPDestination> reg = TestUtils.getInternalState(cut, "lspDestPath");
        assertTrue("No listener registered", !reg.entrySet().isEmpty());
        assertNotNull("Wrong registration key", reg.get("/myProj/:aLang/"));
        assertNotNull("Wrong registration url", reg.get("/myProj/:aLang/").getWebSocketClient());

        // Unregister
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        Mockito.when(request.getReader()).thenReturn(reader);
        Mockito.when(request.getHeader("Register-lsp")).thenReturn("false");
        cut.doPost(request, response);
        reg = TestUtils.getInternalState(cut, "lspDestPath");
        assertTrue("Listener unregistered", reg.entrySet().isEmpty());
    }

    @Test
    public void testDoRegisterLspModule() throws IOException, ServletException {
        String regData = "/myProj/myModule/:aLang/=/testWS/aLang";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        PowerMock.mockStatic(WebSocketClient.class);
        expect(WebSocketClient.getInstance()).andReturn(wsClient);
        replayAll();

        BufferedReader reader;
        // Register
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        Mockito.when(request.getReader()).thenReturn(reader);
        Mockito.when(request.getHeader("Register-lsp")).thenReturn("true");

        cut.doPost(request, response);
        Map<String, LSPDestination> reg = TestUtils.getInternalState(cut, "lspDestPath");
        assertTrue("No listener registered", !reg.entrySet().isEmpty());
        assertNotNull("Wrong registration key", reg.get("/myProj/myModule/:aLang/"));
        assertNotNull("Wrong registration url", reg.get("/myProj/myModule/:aLang/").getWebSocketClient());

        // Unregister
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        Mockito.when(request.getReader()).thenReturn(reader);
        Mockito.when(request.getHeader("Register-lsp")).thenReturn("false");

        cut.doPost(request, response);
        reg = TestUtils.getInternalState(cut, "lspDestPath");
        assertTrue("Listener unregistered", reg.entrySet().isEmpty());

    }

}
