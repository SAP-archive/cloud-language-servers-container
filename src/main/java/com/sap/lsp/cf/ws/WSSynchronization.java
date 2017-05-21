package com.sap.lsp.cf.ws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import java.util.Comparator;
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
	private static final String RESP_FORMAT = "{ \"mapUrl\" : \"%s\" }";
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public WSSynchronization() {
        super();
        // TODO Auto-generated constructor stub
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

		String projRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
		String projPath = SAVE_DIR + projRelPath;

		for (Part part : request.getParts()) {
			String fileName = extractFileName(part);
			// refines the fileName in case it is an absolute path
			fileName = new File(fileName).getName();
			
			String projectRoot = syncProject(part.getInputStream(), new File(projPath));
			
			// Create symbolic link
			Path projectPath = Paths.get(projectRoot);
			Path linkPath = Paths.get(System.getProperty("user.home") + "/" + projectRoot.substring(projectRoot.lastIndexOf('/', projectRoot.length() )+1 ));
			try {
				//LOG.info("CREATING LINK " + linkPath.toString() + " for " + projectRoot.substring(projectRoot.lastIndexOf('/',projectRoot.length())+1) + " Home " + System.getProperty("user.home") );
			    Files.createSymbolicLink(linkPath, projectPath);
			    LOG.info("PROJECT LINK " + linkPath.toString() + " for " + projectPath.toString() + " created.");
			} catch (IOException | UnsupportedOperationException x ) {
			    LOG.severe(x.toString());
			}
			String projMapUrl = "file://" + projectRoot;
			response.setContentType("application/json");
			response.getWriter().append(String.format(RESP_FORMAT, projMapUrl));
		}

		
	}
	
	/**
	 * @see HttpServlet#doPut(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String artifactRelPath = request.getRequestURI().substring(request.getServletPath().length() + 1);
		String artifactPath = SAVE_DIR + artifactRelPath;

		File destination = new File(artifactPath);
		for (Part part : request.getParts()) {
			ZipInputStream zipinputstream = new ZipInputStream(part.getInputStream());
			String destPath =  unpack(zipinputstream, destination, true);

			response.setContentType("application/json");
			response.getWriter().append(String.format(RESP_FORMAT, destPath));
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


}
