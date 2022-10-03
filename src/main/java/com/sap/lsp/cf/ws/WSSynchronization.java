package com.sap.lsp.cf.ws;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.sap.lsp.cf.ws.WSChangeObserver.ChangeType;
import com.sap.lsp.cf.ws.WSChangeObserver.LSPDestination;

/**
 * Servlet implementation class WSSynchronization
 */
@WebServlet(description = "Work Space Synchronization server", urlPatterns = {"/WSSynchronization/*"})
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 2,    // 2MB
        maxFileSize = 1024 * 1024 * 256,        // 256MB
        maxRequestSize = 1024 * 1024 * 256)    // 256MB
public class WSSynchronization extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String SAVE_DIR = System.getenv("HOME") != null ? System.getenv("HOME") + "/di_ws_root" : System.getenv("HOMEPATH") + "/di_ws_root";
    private static final Logger LOG = Logger.getLogger(WSSynchronization.class.getName());
    private static final String FS_STORAGE = "fs-storage";
    private static final String FS_TAGS = "tags";
    private static final String FS_LSP_WS = "LSP-WS";
    private static final String FS_VOLUME_MOUNTS = "volume_mounts";
    private static final String FS_CONTAINER_DIR = "container_dir";
    private static final String SYNC_FILE = ".sync";

    private String wsSaveDir = null;
    private String saveDir;
    private Map<String, LSPDestination> lspDestPath = new ConcurrentHashMap<>();

    /**
     * @see HttpServlet#HttpServlet()
     */
    public WSSynchronization() {
        super();
    }

    @Override
    public void init() throws ServletException {
        String env_vcap_services = System.getenv("VCAP_SERVICES");
        if (env_vcap_services != null) {
            JsonObject volume0 = null;
            JsonReader envReader = Json.createReader(new ByteArrayInputStream(env_vcap_services.getBytes(StandardCharsets.UTF_8)));
            JsonObject cfVCAPEnv = envReader.readObject();
            if (cfVCAPEnv.containsKey(FS_STORAGE)) {
                JsonValue fs0 = cfVCAPEnv.getJsonArray(FS_STORAGE).get(0);
                if (fs0 != null && fs0 instanceof JsonObject) {
                    for (JsonValue tag : ((JsonObject) fs0).getJsonArray(FS_TAGS)) {
                        if (((JsonString) tag).getString().equals(FS_LSP_WS) && ((JsonObject) fs0).containsKey(FS_VOLUME_MOUNTS)) {
                            volume0 = (JsonObject) (((JsonObject) fs0).getJsonArray(FS_VOLUME_MOUNTS)).get(0);
                            break;
                        }
                    }
                    if (volume0 != null) {
                        if (volume0.containsKey(FS_CONTAINER_DIR)) {
                            String volPath = volume0.getString(FS_CONTAINER_DIR);
                            File di_ws = new File(new File(volPath), "di_ws_root");
                            if (!di_ws.exists()) {
                                if (di_ws.mkdir()) {
                                    wsSaveDir = di_ws.getAbsolutePath();
                                }
                            } else {
                                wsSaveDir = di_ws.getAbsolutePath();
                            }
                        }
                    }
                }
            }
        }
        this.saveDir = wsSaveDir != null ? wsSaveDir + "/" : SAVE_DIR + "/";

    }

    protected String getSaveDir() {
        return this.wsSaveDir;
    }

    void setSaveDir(String saveDir) {
        this.wsSaveDir = saveDir;
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (checkSync()) {
            response.setContentType("application/json");
            String workspaceSaveDir = getWorkspaceSaveDir();
            File syncFile = new File(new File(workspaceSaveDir), SYNC_FILE);
            response.getWriter().append(String.format("{ \"syncTimestamp\": \"%s\"}", Files.getLastModifiedTime(syncFile.toPath()).toString()));
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    void initialSync(HttpServletRequest request, HttpServletResponse response, String workspaceRoot, String workspaceSaveDir) throws IOException, ServletException {
        // Expected: one part containing zip
        Part part = request.getParts().iterator().next();
        try (final InputStream inputStream = part.getInputStream()) {
            syncWorkspace(inputStream, new File(workspaceSaveDir));
            response.getWriter().append(workspaceRoot);
            response.setStatus(HttpServletResponse.SC_CREATED);
        } catch (NoSuchElementException ePart) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        }
    }

    void cleanUpWS(Path rootPath) throws IOException {
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
                String workspaceSaveDir = getWorkspaceSaveDir();
                initialSync(request, response, "file://" + workspaceSaveDir, workspaceSaveDir);
            } else if (requestURI.length() > servletPath.length()) {
                if (!checkSync()) {
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

    private String getWorkspaceSaveDir() {
        return wsSaveDir != null ? wsSaveDir + "/" : SAVE_DIR;
    }

    private void addNewFiles(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String artifactRelPath;
        artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
        File destination = new File(FilenameUtils.normalize(this.saveDir + artifactRelPath));
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
        if (!checkSync()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        String artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
        String artifactPath = this.saveDir + artifactRelPath;

        File destination = new File(FilenameUtils.normalize(artifactPath));
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
        if (!checkSync()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        String artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
        String artifactPath = this.saveDir + artifactRelPath;
        File destination = new File(FilenameUtils.normalize(artifactPath));
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
        changeObserver.onChangeReported(artifactRelPath, this.saveDir);
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
                if (!newFile.toPath().normalize().startsWith(destination.toPath().normalize())) {
                    throw new IOException("Bad zip entry");
                }
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
        final List<String> extracted = unpack(inputstream, new File(this.saveDir));
        for (String artifactRelPath : extracted) {
            changeObserver.onChangeReported(artifactRelPath, this.saveDir);
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
                lspDestPath.put(path, new LSPDestination(dest, WebSocketClient.getInstance()));
                LOG.info("WS Sync dest registered " + path + " dest " + dest);
            } else {
                if (lspDestPath.remove(path) != null) LOG.info("WS Sync unregistered " + path);
            }
        } catch (IOException e) {
            LOG.severe("WS Sync - can't register LSP destination for notifications due to " + e.getMessage());
        }


    }

    private boolean checkSync() {
        String workspaceSaveDir = this.saveDir;
        File fSyncts = new File(new File(workspaceSaveDir), SYNC_FILE);
        return fSyncts.exists();
    }


}
