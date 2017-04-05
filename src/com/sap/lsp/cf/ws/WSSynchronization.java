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
@WebServlet(description = "Work Space Synchronization server", urlPatterns = { "/WSSynchronization" })
@MultipartConfig(fileSizeThreshold=1024*1024*2,	// 2MB
maxFileSize=1024*1024*10,		// 10MB
maxRequestSize=1024*1024*50)	// 50MB
public class WSSynchronization extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String SAVE_DIR = System.getenv("HOME") + "/.java-buildpack/language_server_bin_exec_jdt/di_ws_root/";
	private static final Logger LOG = Logger.getLogger(WSSynchronization.class.getName());
       
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
		append(" for curl -i -X POST -H \"Content-Type: multipart/form-data\" -F \"file=@<YourZipFile>\"  http://<application>/WSSynchronization");
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		for (Part part : request.getParts()) {
			String fileName = extractFileName(part);
			// refines the fileName in case it is an absolute path
			fileName = new File(fileName).getName();
			//part.write(savePath + File.separator + fileName);
			String projPath = SAVE_DIR + fileName.substring(0, fileName.indexOf(".zip"));
			//String projectRoot = unzipProject(part.getInputStream(), new File(projPath));
			String projectRoot = unzipProject(part.getInputStream(), new File(SAVE_DIR));
			
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
			response.getWriter().append("Uploaded at  " + projectRoot);
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
	
	public String unzipProject(InputStream projectPart, File destination) {
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
		return unpack(zipinputstream, destination);
	}

	private String unpack(ZipInputStream zipinputstream, File destination) {
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
		                if( !newFile.mkdirs() ) LOG.warning("Directory creation error");
		                zipentry = zipinputstream.getNextEntry();
		                continue;
		            } else {
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
			LOG.warning("UNZIP " + e.toString());
		}
        
        return projectRoot;

	}


}
