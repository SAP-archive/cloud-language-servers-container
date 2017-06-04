package com.sap.lsp.cf.ws;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Servlet implementation class WSSynchronization
 */
@WebServlet(description = "Work Space Synchronization server", urlPatterns = { "/WSSynchronization/*" })
@MultipartConfig(fileSizeThreshold=1024*1024*2,	// 2MB
maxFileSize=1024*1024*10,		// 10MB
maxRequestSize=1024*1024*50)	// 50MB
public class WSSynchronization extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String SAVE_DIR = System.getenv("HOME") + "/di_ws_root/"; 
	private static final Logger LOG = Logger.getLogger(WSSynchronization.class.getName());
	private static final String RESP_FORMAT = "{ \"mapUrl\": \"%s\", \"module\": \"%s\" }";
	private static final String FS_STORAGE = "fs-storage";
	private static final String FS_TAGS = "tags";
	private static final String FS_LSP_WS = "LSP-WS";
	private static final String FS_VOLUME_MOUNTS = "volume_mounts";
	private static final String FS_CONTAINER_DIR = "container_dir";
	private String wsSaveDir = null;
	private WebSocketClient wsLSP = null;
	
	private static final int CHANGE_CREATED = 1;
	private static final int CHANGE_CHANGED = 2;
	private static final int CHANGE_DELETED = 3;
	
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public WSSynchronization() {
        super();
        // TODO Auto-generated constructor stub
    }
    
    @Override
    public void init() throws ServletException {
    	String env_vcap_services = System.getenv("VCAP_SERVICES");
    	if (env_vcap_services != null) {
	    	JsonObject volume0 = null;
			JsonReader envReader = Json.createReader(new ByteArrayInputStream(env_vcap_services.getBytes(StandardCharsets.UTF_8)));
			JsonObject cfVCAPEnv = envReader.readObject();
			if ( cfVCAPEnv.containsKey(FS_STORAGE)) {
				JsonValue fs0 = cfVCAPEnv.getJsonArray(FS_STORAGE).get(0);
				if ( fs0 != null && fs0 instanceof JsonObject ) {
					for ( JsonValue tag: ((JsonObject)fs0).getJsonArray(FS_TAGS)) {
						if ( ((JsonString)tag).getString().equals(FS_LSP_WS) && ((JsonObject)fs0).containsKey(FS_VOLUME_MOUNTS) ) {
							volume0 = (JsonObject)(((JsonObject)fs0).getJsonArray(FS_VOLUME_MOUNTS)).get(0);
							break;
						}
					}
					if ( volume0 != null ) {
						if ( volume0.containsKey(FS_CONTAINER_DIR)) {
							String volPath = volume0.getString(FS_CONTAINER_DIR);
							File di_ws = new File(new File(volPath), "di_ws_root");
							if ( !di_ws.exists() )  {
								if (di_ws.mkdir()) {
									wsSaveDir  = di_ws.getAbsolutePath();
								}
							} else {
								wsSaveDir  = di_ws.getAbsolutePath();
							}
						}
					}
				}
			}
    	}
    	
    	wsLSP  = new WebSocketClient();

    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		response.getWriter().append("Served at: ").append(request.getContextPath()).
		append(" for curl -i -X PUT -H \"Content-Type: multipart/form-data\" -F \"file=@<YourZipFile>\"  https://<application>/WSSynchronization");
	}

	/**
	 * @see HttpServlet#doPut(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		String artifactRelPath = "";
		String projPath = null;
		String moduleRoot = null;
		File destination = null;
		List<String> extracted = null;
		artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1 );
		String saveDir =  wsSaveDir != null ? wsSaveDir + "/" : SAVE_DIR;
		if ( artifactRelPath.indexOf('/') < 0) {
			projPath = saveDir + artifactRelPath;
		} else if ( artifactRelPath.indexOf('/') > 0 ) {
			destination = new File(saveDir + artifactRelPath);
			if ( destination.exists()) {
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		}
		for (Part part : request.getParts()) {
			String fileName = extractFileName(part);
			// refines the fileName in case it is an absolute path
			fileName = new File(fileName).getName();
			
			if ( projPath != null ) {
				File projRootDir = new File(new File(saveDir), artifactRelPath);
				moduleRoot = "file://" + syncProject(part.getInputStream(), projRootDir);
			} else {
				String zipPath = artifactRelPath.substring(artifactRelPath.indexOf('/') + 1);
				extracted = extract(new ZipInputStream(part.getInputStream()), destination, zipPath);
			}
			// Create symbolic link
/*			Path projectPath = Paths.get(moduleRoot);
			Path linkPath = Paths.get(System.getProperty("user.home") + "/" + moduleRoot.substring(moduleRoot.lastIndexOf('/', moduleRoot.length() )+1 ));
			try {
				//LOG.info("CREATING LINK " + linkPath.toString() + " for " + projectRoot.substring(projectRoot.lastIndexOf('/',projectRoot.length())+1) + " Home " + System.getProperty("user.home") );
			    Files.createSymbolicLink(linkPath, projectPath);
			    LOG.info("PROJECT LINK " + linkPath.toString() + " for " + projectPath.toString() + " created.");
			} catch (IOException | UnsupportedOperationException x ) {
			    LOG.severe(x.toString());
			}*/
			response.setStatus(HttpServletResponse.SC_CREATED);
			response.setContentType("application/json");
			if ( projPath != null ) {
				String projMapUrl = "file://" + projPath;
			
				response.getWriter().append(String.format(RESP_FORMAT, projMapUrl, moduleRoot));
			} else {
				response.getWriter().append("{}");
			}
		}

		if ( extracted != null ) {
			if ( wsLSP.isClosed() ) {
				wsLSP.connect("ws://localhost:8080/LanguageServer?local");
			}
			String msg = buildLSPNotification(CHANGE_CREATED, extracted);
			wsLSP.sendNotification(msg);
		}
	}
	
	/**
	 * @see HttpServlet#doPut(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
		String saveDir =  wsSaveDir != null ? wsSaveDir + "/" : SAVE_DIR;
		String artifactPath = saveDir + artifactRelPath;
		List<String> extracted = null;
		
		File destination = new File(artifactPath);
		String zipPath = artifactRelPath.substring(artifactRelPath.indexOf('/') + 1);
		for (Part part : request.getParts()) {
			ZipInputStream zipinputstream = new ZipInputStream(part.getInputStream());
			if ( destination.exists() && destination.isDirectory()) {
				//moduleRoot = "file://" + syncProject(part.getInputStream(), destination);
			} else if (destination.exists()) {
				 extracted = extract(zipinputstream, destination, zipPath);
			}
			if ( wsLSP.isClosed() ) {
				wsLSP.connect("ws://localhost:8080/LanguageServer?local");
			}
			String msg = buildLSPNotification(CHANGE_CHANGED, extracted);
			wsLSP.sendNotification(msg);
		}
		response.setContentType("application/json");
		response.getWriter().append(String.format("{ \"updated\": \"%s\"}", artifactRelPath));

	}


	/**
	 * @see HttpServlet#doPut(HttpServletRequest request, HttpServletResponse response)
	 */	
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
		String saveDir =  wsSaveDir != null ? wsSaveDir + "/" : SAVE_DIR;
		String artifactPath = saveDir + artifactRelPath;
		List<String> deleted  = new ArrayList<String>();

		File destination = new File(artifactPath);
		if ( !destination.exists() ) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		} else if ( !destination.delete() ) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} else {
			response.setContentType("application/json");
			response.getWriter().append(String.format("{ \"deleted\": \"%s\"}", artifactRelPath));
			if ( wsLSP.isClosed() ) {
				wsLSP.connect("ws://localhost:8080/LanguageServer?local");
			}
			deleted.add(artifactPath);
			String msg = buildLSPNotification(CHANGE_DELETED, deleted);
			wsLSP.sendNotification(msg);
		}
	}
	
/* ---------------------- Private methods ------------------------------------	*/
	
	private String extractFileName(Part part) {
		String contentDisp = part.getHeader("content-disposition");
		String[] items = contentDisp.split(";");
		for (String s : items) {
			if (s.trim().startsWith("filename")) {
				return s.substring(s.indexOf("=") + 2, s.length()-1);
			}
		}
		return "";
	}
	
	public String syncProject(InputStream projectPart, File destination) {
		if ( destination.exists()) {
			Path rootPath = Paths.get(destination.getPath());
			try {
				Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
				    .sorted(Comparator.reverseOrder())
				    .map(Path::toFile)
				    .forEach(File::delete);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOG.warning( e.toString());
			}			
		}
		if ( !destination.exists() ) { if (!destination.mkdirs()) {
			LOG.severe("Can't create project path " + destination.getAbsolutePath());
			}
		}
		
		LOG.info("Unzip project to " + destination.getAbsolutePath());
		ZipInputStream zipinputstream = new ZipInputStream(projectPart);
		return unpack(zipinputstream, destination, false);
	}

	private String unpack(ZipInputStream zipinputstream, File destination, boolean update) {
		String projectRoot = "";
        byte[] buf = new byte[1024];
        ZipEntry zipentry;
        
        try {
			zipentry = zipinputstream.getNextEntry();
		        while (zipentry != null) {
		            int n;
		            FileOutputStream fileoutputstream;
		            
		            File newFile = new File(destination, zipentry.getName());
		            LOG.info("UNZIP Creating " + newFile.getAbsolutePath());
		            
		            if (zipentry.isDirectory()) {
		            	if ( newFile.exists()) {
			                zipentry = zipinputstream.getNextEntry();
			                continue;
		            	}
		            	
		                if( !newFile.mkdirs() ) {
		                	LOG.warning("Directory creation error");
		                	throw new IOException("Directory creation error");
		                } else {
			                zipentry = zipinputstream.getNextEntry();
			                continue;
		                }
		            } else {
		            	if ( newFile.exists() && !update ) {
		                	LOG.warning("File creation error");
		                	throw new IOException("File creation error");		            		
		            	}
		            	if ( newFile.getName().equals("pom.xml") )
		            		projectRoot = newFile.getParent();	
		            }
		
		            fileoutputstream = new FileOutputStream(newFile);
		
		            while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
		                fileoutputstream.write(buf, 0, n);
		            }
		
		            fileoutputstream.close();
		            zipinputstream.closeEntry();
		            if ( !newFile.exists()) LOG.warning("File creation error");
		            zipentry = zipinputstream.getNextEntry();
		
		        }
			
		
		    zipinputstream.close();	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			LOG.warning("UNZIP error: " + e.toString());
		}
        
        return projectRoot;

	}
	
	private List<String> extract(ZipInputStream zipinputstream, File destination, String zipPath) {
        byte[] buf = new byte[1024];
        ZipEntry zipentry;
        List<String> extracted = new ArrayList<String>();
        
        try {
			zipentry = zipinputstream.getNextEntry();
		        while (zipentry != null) {
		            int n;
		            FileOutputStream fileoutputstream;
		            
		            if ( !zipentry.getName().equals(zipPath)) {
		            	zipentry = zipinputstream.getNextEntry();
		            	continue;
		            }
		            
		            File newFile = destination;
		            LOG.info("UNZIP Updating " + newFile.getAbsolutePath());
		            
/*		            if (zipentry.isDirectory()) {
		            	if ( newFile.exists()) {
			                zipentry = zipinputstream.getNextEntry();
			                continue;
		            	}
		            	
		                if( !newFile.mkdirs() ) {
		                	LOG.warning("Directory creation error");
		                	throw new IOException("Directory creation error");
		                } else {
			                zipentry = zipinputstream.getNextEntry();
			                continue;
		                }
		            }*/
		
		            fileoutputstream = new FileOutputStream(newFile);
		
		            while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
		                fileoutputstream.write(buf, 0, n);
		            }
		
		            fileoutputstream.close();
		            zipinputstream.closeEntry();
		            if ( !newFile.exists()) LOG.warning("File creation error");
		            extracted.add(newFile.getPath());
		            break;
		            
		
		        }
			
		
		    zipinputstream.close();	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			LOG.warning("UNZIP error: " + e.toString());
		}
        
		return extracted;
	}
	
	private String buildLSPNotification(int type, List<String> artifacts) {
		
		JsonArrayBuilder changes = Json.createArrayBuilder();
		for (String sUrl: artifacts) {
			changes.add(Json.createObjectBuilder().add("uri",sUrl).add("type", type).build());
		}

		JsonObject bodyObj = Json.createObjectBuilder()
				.add("jsonrpc", "2.0")
				.add("method", "workspace/didChangeWatchedFiles")
				.add("params", Json.createObjectBuilder()
						.add("changes", changes.build())
						.build())
				.build();
		String body = bodyObj.toString();
		String msg = String.format("Content-Length: %d\r\n\r\n%s", body.length(), body);
		return msg;
	}



}
