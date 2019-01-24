package com.sap.lsp.cf.ws;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.sap.lsp.cf.ws.WSChangeObserver.ChangeType;
import com.sap.lsp.cf.ws.WSChangeObserver.LSPDestination;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

/**
 * Servlet implementation class WSSynchronization
 */
@WebServlet(description = "Work Space Synchronization server", urlPatterns = {"/WSSynchronization/*"})
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2,    // 2MB
        maxFileSize = 1024 * 1024 * 256,        // 256MB
        maxRequestSize = 1024 * 1024 * 256)    // 256MB
public class WSSynchronization extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(WSSynchronization.class.getName());
    private static final String SYNC_FILE = ".sync";
    private String WORKSPACE_ROOT = System.getenv("WORKSPACE_ROOT");

    private Map<String, LSPDestination> lspDestPath = new ConcurrentHashMap<>();
    private WebSocketClientFactory webSocketClientFactory = new WebSocketClientFactory();

    /**
     * @see HttpServlet#HttpServlet()
     */
    public WSSynchronization() {
        super();
    } 
    
    void setTestContext(WebSocketClientFactory webSocketClientFactory,
                        Map<String, LSPDestination> lspDestPath) throws ServletException {
        this.webSocketClientFactory = webSocketClientFactory;
        this.lspDestPath = lspDestPath;
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (isWorkspaceSynced()) {
            response.setContentType("application/json");
            File syncFile = new File(new File(WORKSPACE_ROOT), SYNC_FILE);
            response.getWriter().append(String.format("{ \"syncTimestamp\": \"%s\"}", Files.getLastModifiedTime(syncFile.toPath()).toString()));
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    void initialSync(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // Expected: one part containing zip
        Part part = request.getParts().iterator().next();
        try (final InputStream inputStream = part.getInputStream()) {
            syncWorkspace(inputStream, new File(WORKSPACE_ROOT));
            response.getWriter().append(WORKSPACE_ROOT);
            response.setStatus(HttpServletResponse.SC_CREATED);
        } catch (NoSuchElementException ePart) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        }
    }

    private void cleanUpWS(Path rootPath) throws IOException {
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
                .sorted(Comparator.reverseOrder())
                .filter((p) -> !p.equals(rootPath))
                .map(Path::toFile)
                .map(File::delete)
                .reduce((a, b) -> a && b)
                .ifPresent(isSuccess -> LOG.warning("Some delete operation failed"));
    }

    /**
     * @see HttpServlet#doPut(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String servletPath = request.getServletPath();
            String requestURI = request.getRequestURI();
            LOG.info("Inside doPut with path " + requestURI);
            if (servletPath.equals(requestURI)) {
                initialSync(request, response);
            } else if (requestURI.length() > servletPath.length()) {
                if (!isWorkspaceSynced()) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return;
                }
                addNewFiles(request, response);
            }
        } catch (Exception e) {
            LOG.severe("doPut failed: " + e + " Trace: " + e.getStackTrace());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void addNewFiles(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String artifactRelPath;
        artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
        File destination = new File(FilenameUtils.normalize(WORKSPACE_ROOT + "/" + artifactRelPath));
        if (destination.exists()) {
            LOG.warning("File to be added already exist: " + destination.getPath() +
                    ", can happen on project creation flow. Extracting new version...");
        }
        // Expected: one part containing zip
        try {
            Part part = request.getParts().iterator().next();
            WSChangeObserver changeObserver = new WSChangeObserver(ChangeType.CHANGE_CREATED, lspDestPath);
            if (extract(part.getInputStream(), changeObserver)) {
                changeObserver.notifyLSP();
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_CREATED);
            } else {
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (NoSuchElementException ePart) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        }
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Check if it is LSP registration
        String lspReg = request.getHeader("Register-lsp");
        if (lspReg != null) {
            handleLSPDest(Boolean.parseBoolean(lspReg), request.getReader());
            return;
        }

        // Otherwise process data passed from DI
        if (!isWorkspaceSynced()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        String artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);

        File destination = new File(FilenameUtils.normalize(WORKSPACE_ROOT + "/" + artifactRelPath));
        // Expected: one part containing zip
        try {
            Part part = request.getParts().iterator().next();

            WSChangeObserver changeObserver = new WSChangeObserver(ChangeType.CHANGE_UPDATED, lspDestPath);
            if (destination.exists() && !destination.isDirectory() && extract(part.getInputStream(), changeObserver)) {
                changeObserver.notifyLSP();
                response.setContentType("application/json");
                response.getWriter().append(String.format("{ \"updated\": \"%s\"}", artifactRelPath));
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (NoSuchElementException ePart) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        }
    }

    /**
     * @see HttpServlet#doDelete(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!isWorkspaceSynced()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        String artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
        File destination = new File(FilenameUtils.normalize(WORKSPACE_ROOT + "/" + artifactRelPath));
        try {
            FileUtils.forceDelete(destination);
        } catch (FileNotFoundException e) {
            response.setContentType("application/json");
            response.getWriter().append(String.format("{ \"notExists\": \"%s\"}", destination.getPath()));
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            response.setContentType("application/json");
            response.getWriter().append(String.format("{ \"error\": \"%s\"}", destination.getPath()));
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        response.setContentType("application/json");
        response.getWriter().append(String.format("{ \"deleted\": \"%s\"}", artifactRelPath));
        WSChangeObserver changeObserver = new WSChangeObserver(ChangeType.CHANGE_DELETED, lspDestPath);
        changeObserver.onChangeReported(artifactRelPath, WORKSPACE_ROOT);
        changeObserver.notifyLSP();
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /* ---------------------- Private methods ------------------------------------	*/

    private void syncWorkspace(InputStream workspaceZipStream, File destination) throws IOException {
        if (destination.exists() && workspaceZipStream != null) {
            Path rootPath = Paths.get(destination.getPath());
            cleanUpWS(rootPath);
        }
        if (!destination.exists() && !destination.mkdirs()) {
            LOG.severe("Can't create workspace path " + destination.getAbsolutePath());
        }

        LOG.info("Unzip workspace to " + destination.getAbsolutePath());
        unpack(workspaceZipStream, destination);

        // Create sync label file
        long timestamp = System.currentTimeMillis();
        File syncFile = new File(destination, SYNC_FILE);
        new FileOutputStream(syncFile).close();
        syncFile.setLastModified(timestamp);
    }

    private List<String> unpack(InputStream zipStream, File destination) {
        byte[] buf = new byte[1024];
        ZipEntry zipentry;
        List<String> extracted = new ArrayList<>();
		
        try (ZipInputStream zipInputStream = new ZipInputStream(zipStream)) {
            while ((zipentry = zipInputStream.getNextEntry()) != null) {
                File newFile = new File(destination, zipentry.getName());
                LOG.info("UNZIP Creating " + newFile.getAbsolutePath());

                if (zipentry.isDirectory()) {
                    if (!newFile.exists()) {
                        newFile.mkdirs();
                    }
                } else { // zipentry is a file
                    File parentFile = newFile.getParentFile();
                    if (!parentFile.exists()) {
                	    parentFile.mkdirs();
                    }
                    try (FileOutputStream fileoutputstream = new FileOutputStream(newFile)) {
                        int n;
                        while ((n = zipInputStream.read(buf, 0, 1024)) > -1) {
                            fileoutputstream.write(buf, 0, n);
                        }
                    }
                    extracted.add(zipentry.getName());
                }
                       
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            LOG.warning("UNZIP error: " + e.toString());
        }
        return extracted;
    }

    private boolean extract(InputStream inputstream, WSChangeObserver changeObserver) {
        final List<String> extracted = unpack(inputstream, new File(WORKSPACE_ROOT));
        for (String artifactRelPath : extracted) {
            changeObserver.onChangeReported(artifactRelPath, WORKSPACE_ROOT);
        }
        return extracted.size() > 0;
    }

    private void handleLSPDest(boolean bReg, BufferedReader reader) {
        try {
            String pathMap = URLDecoder.decode(reader.readLine(), "UTF-8");
            if (pathMap.length() == 0 || !pathMap.contains("=")) {
                return;
            }
            String pm[] = pathMap.split("=");
            String path = pm[0];
            String dest = pm[1];
            if (bReg) {
                lspDestPath.put(path, new LSPDestination(dest, webSocketClientFactory.createInstance()));
                LOG.info("WS Sync dest registered " + path + " dest " + dest);
            } else {
                if (lspDestPath.remove(path) != null) LOG.info("WS Sync unregistered " + path);
            }
        } catch (IOException e) {
            LOG.severe("WS Sync - can't register LSP destination for notifications due to " + e.getMessage());
        }
    }

    private boolean isWorkspaceSynced() {
        return new File(new File(WORKSPACE_ROOT), SYNC_FILE).exists();
    }

}
