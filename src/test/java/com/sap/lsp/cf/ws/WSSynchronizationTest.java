package com.sap.lsp.cf.ws;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.sap.lsp.cf.ws.WSChangeObserver.LSPDestination;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class WSSynchronizationTest {

    private static final String SERVLET_PATH = "http://localhost:8000/WSSynchronization";
    private static final String ARTIFACT_PATH = "/myProject/myModule/java/test.java";
    private static final String NEW_ARTIFACT_PATH = "/newProject/newModule/java1/test1.java";

    private WSSynchronization wsSynchronization;
    private String workspaceRootPath;
    private Map<String, LSPDestination> lspDestPath = new ConcurrentHashMap<>();

    @Rule
    public TemporaryFolder workspaceRoot = new TemporaryFolder();
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Mock
    private WebSocketClientFactory webSocketClientFactory;
    @Mock
    private WebSocketClient webSocketClient;
    @Mock
    private PrintWriter responseWriter;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Part part;
    @Mock
    private ServletConfig servletConfig;

    
    private InputStream getZipStream(String zipFileName) throws FileNotFoundException {
        return getClass().getClassLoader().getResourceAsStream(zipFileName);
    }
        
    @Before
    public void setup() throws IOException, ServletException {
        workspaceRootPath = workspaceRoot.getRoot().getPath();

        when(webSocketClientFactory.createInstance()).thenReturn(webSocketClient);
        when(servletConfig.getInitParameter("workspaceRoot")).thenReturn(workspaceRootPath);
        when(response.getWriter()).thenReturn(responseWriter);

        environmentVariables.set("WORKSPACE_ROOT", workspaceRootPath);
        wsSynchronization = new WSSynchronization();
        wsSynchronization.setTestContext(webSocketClientFactory, lspDestPath);
    }

    @After
    public void teardown() {
        lspDestPath.clear();
    }

    @Test
    public void testDoGet() throws FileNotFoundException, IOException, ServletException {
        when(part.getInputStream()).thenReturn(getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.initialSync(request, response);

        reset(request);
        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(request.getRequestURI()).thenReturn(SERVLET_PATH);

        wsSynchronization.doGet(request, response);
        verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void testDoGetSyncLost() throws FileNotFoundException, IOException, ServletException {
        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(request.getRequestURI()).thenReturn(SERVLET_PATH);

        wsSynchronization.doGet(request, response);

        verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    public void testDoPutInit() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(request.getRequestURI()).thenReturn(SERVLET_PATH);

        when(part.getInputStream()).thenReturn(getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.doPut(request, response);
        verify(response, times(1)).setStatus(HttpServletResponse.SC_CREATED);
        assertTrue("File create ", new File(workspaceRootPath + File.separator + "myProject"
                + File.separator + "myModule"
                + File.separator + "java"
                + File.separator + "test.java").exists());
        assertTrue("File create ", new File(workspaceRootPath + File.separator + ".sync").exists());
    }
    
    @Test
    public void testNewProject() throws IOException, ServletException {
        when(part.getInputStream()).thenReturn(getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.initialSync(request, response);

        reset(request, part);
        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(request.getRequestURI()).thenReturn(SERVLET_PATH + NEW_ARTIFACT_PATH);

        when(part.getInputStream()).thenReturn(getZipStream("newProject.zip"));
        parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.doPut(request, response);
        verify(response, times(2)).setStatus(HttpServletResponse.SC_CREATED);
        //'/newProject/newModule/java1/test1.java'
        assertTrue("File create ", new File(workspaceRootPath + File.separator + "newProject"
                + File.separator + "newModule"
                + File.separator + "java1"
                + File.separator + "test1.java").exists());
    }
    
    @Test
    public void testNewProjectWhenZipContainsOnlyFullPathEntry() throws IOException, ServletException {
        when(part.getInputStream()).thenReturn(getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.initialSync(request, response);
        
        reset(request, part);

        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(request.getRequestURI()).thenReturn(SERVLET_PATH + NEW_ARTIFACT_PATH);

        when(part.getInputStream()).thenReturn(getZipStream("newProjectWithOnlyFullPAthEntry.zip"));
        parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.doPut(request, response);
        verify(response, times(2)).setStatus(HttpServletResponse.SC_CREATED);
        assertTrue("File create ", new File(workspaceRootPath + File.separator + "newProject"
                + File.separator + "newModule"
                + File.separator + "java1"
                + File.separator + "test1.java").exists());
    }
    
    @Test
    public void testDoPutSyncLost() throws IOException, ServletException {

        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(request.getRequestURI()).thenReturn(SERVLET_PATH + NEW_ARTIFACT_PATH);

        when(part.getInputStream()).thenReturn(getZipStream("newProject.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.doPut(request, response);

        verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }


    @Test
    public void testDoPost() throws IOException, ServletException {
        when(part.getInputStream()).thenReturn(getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.initialSync(request, response);

        reset(request, part);
        when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(part.getInputStream()).thenReturn(getZipStream("postTest.zip"));
        parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.doPost(request, response);
        verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);
        assertTrue("File create ", new File(workspaceRootPath + File.separator + "myProject"
                + File.separator + "myModule"
                + File.separator + "java"
                + File.separator + "test.java").exists());
    }

    @Test
    public void testDoPostSyncLost() throws IOException, ServletException {

        when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
        when(request.getServletPath()).thenReturn(SERVLET_PATH);
        when(part.getInputStream()).thenReturn(getZipStream("postTest.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.doPost(request, response);

        verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }


    @Test
    public void testDoDelete() throws IOException, ServletException {
        when(part.getInputStream()).thenReturn(getZipStream("putTest.zip"));
        Collection<Part> parts = Collections.singleton(part);
        when(request.getParts()).thenReturn(parts);

        wsSynchronization.initialSync(request, response);
        
        reset(request);

        when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
        when(request.getServletPath()).thenReturn(SERVLET_PATH);

        wsSynchronization.doDelete(request, response);
        verify(response, times(1)).setStatus(HttpServletResponse.SC_OK);
        assertFalse("File create ", new File(workspaceRootPath + File.separator + "myProject"
                + File.separator + "myModule"
                + File.separator + "java"
                + File.separator + "test.java").exists());
    }

    @Test
    public void testDoDeleteSyncLost() throws IOException, ServletException {

        when(request.getRequestURI()).thenReturn(SERVLET_PATH + ARTIFACT_PATH);
        when(request.getServletPath()).thenReturn(SERVLET_PATH);

        wsSynchronization.doDelete(request, response);
        verify(response, times(1)).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    public void testDoRegisterLspWS() throws IOException, ServletException {
        String regData = "/:aLang=/testWS/aLang";

        BufferedReader reader;
        // Register
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        when(request.getReader()).thenReturn(reader);
        when(request.getHeader("Register-lsp")).thenReturn("true");

        wsSynchronization.doPost(request, response);
        assertTrue("No listener registered", !lspDestPath.entrySet().isEmpty());
        assertNotNull("Wrong registration key", lspDestPath.get("/:aLang"));
        assertNotNull("Wrong registration url", lspDestPath.get("/:aLang").getWebSocketClient());

        // Unregister
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        when(request.getReader()).thenReturn(reader);
        when(request.getHeader("Register-lsp")).thenReturn("false");

        wsSynchronization.doPost(request, response);
        assertTrue("Listener unregistered", lspDestPath.entrySet().isEmpty());
    }

    @Test
    public void testDoRegisterLspProj() throws IOException, ServletException {
        String regData = "/myProj/:aLang/=/testWS/aLang";

        BufferedReader reader;
        // Register
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        when(request.getReader()).thenReturn(reader);
        when(request.getHeader("Register-lsp")).thenReturn("true");

        wsSynchronization.doPost(request, response);
        assertTrue("No listener registered", !lspDestPath.entrySet().isEmpty());
        assertNotNull("Wrong registration key", lspDestPath.get("/myProj/:aLang/"));
        assertNotNull("Wrong registration url", lspDestPath.get("/myProj/:aLang/").getWebSocketClient());

        // Unregister
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        when(request.getReader()).thenReturn(reader);
        when(request.getHeader("Register-lsp")).thenReturn("false");
        wsSynchronization.doPost(request, response);
        assertTrue("Listener unregistered", lspDestPath.entrySet().isEmpty());
    }

    @Test
    public void testDoRegisterLspModule() throws IOException, ServletException {
        String regData = "/myProj/myModule/:aLang/=/testWS/aLang";

        BufferedReader reader;
        // Register
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        when(request.getReader()).thenReturn(reader);
        when(request.getHeader("Register-lsp")).thenReturn("true");

        wsSynchronization.doPost(request, response);
        assertTrue("No listener registered", !lspDestPath.entrySet().isEmpty());
        assertNotNull("Wrong registration key", lspDestPath.get("/myProj/myModule/:aLang/"));
        assertNotNull("Wrong registration url", lspDestPath.get("/myProj/myModule/:aLang/").getWebSocketClient());

        // Unregister
        try (InputStream is = new ByteArrayInputStream(regData.getBytes())) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        when(request.getReader()).thenReturn(reader);
        when(request.getHeader("Register-lsp")).thenReturn("false");

        wsSynchronization.doPost(request, response);
        assertTrue("Listener unregistered", lspDestPath.entrySet().isEmpty());

    }

}
