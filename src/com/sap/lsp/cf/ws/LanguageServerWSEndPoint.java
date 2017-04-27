package com.sap.lsp.cf.ws;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.ProcessBuilder.Redirect;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

@WebListener
@ServerEndpoint(value="/LanguageServer")
public class LanguageServerWSEndPoint implements ServletContextListener {

	public static final String ENV_IPC = "IPC";
	public static final String ENV_IPC_SOCKET = "socket";
	public static final String ENV_IPC_PIPES = "pipes";
	public static final String IPC_IN = "in";
	public static final String IPC_OUT = "out";
	
	public static final String BASE_DIR = "/home/vcap/app/.java-buildpack/";
	public static final String ENV_LAUNCHER = "exec";
	public static final String DEBUG_CLIENT = "debugclient";
	
	private final String launcherScript;
	
	private static ServletContext servletContext;
	private EndpointConfig config;
	
	private static final Logger LOG = Logger.getLogger(LanguageServerWSEndPoint.class.getName());

	private PrintWriter inWriter = null;
    private Reader out = null;
	private Process process;
	
	
	private enum IPC { SOCKET, NAMEDPIPES, STREAM };
	private final IPC ipc;
	ServerSocket serverSocketIn = null;
	ServerSocket serverSocketOut = null;
	
	private static class OutputStreamHandler extends Thread {

	    public static final String JSONRPC_VERSION = "2.0";
	    public static final String CONTENT_LENGTH_HEADER = "Content-Length";
	    public static final String CONTENT_TYPE_HEADER = "Content-Type";
	    public static final String JSON_MIME_TYPE = "application/json";
	    public static final String CRLF = "\r\n";
	    
		private final Basic remote;
		private final BufferedReader out;
		//private final InputStreamReader pipeReader;
		private boolean keepRunning;
		
		   private static class Headers {
		        int contentLength = -1;
		        String charset = StandardCharsets.UTF_8.name();
		    }	
		
		public OutputStreamHandler( RemoteEndpoint.Basic remoteEndpointBasic, BufferedReader out) {
			this.remote = remoteEndpointBasic;
			this.out = out;
		}

		protected void fireError(Throwable exception) {
			LOG.warning(exception.getMessage()); 
		}

	    protected void parseHeader(String line, Headers headers) {
	        int sepIndex = line.indexOf(':');
	        if (sepIndex >= 0) {
	            String key = line.substring(0, sepIndex).trim();
	            switch (key) {
	                case CONTENT_LENGTH_HEADER:
	                    try {
	                        headers.contentLength = Integer.parseInt(line.substring(sepIndex + 1).trim());
	                    } catch (NumberFormatException e) {
	                        fireError(e);
	                    }
	                    break;
	                case CONTENT_TYPE_HEADER: {
	                    int charsetIndex = line.indexOf("charset=");
	                    if (charsetIndex >= 0)
	                        headers.charset = line.substring(charsetIndex + 8).trim();
	                    break;
	                }
	            }
	        }
	    }

	    protected void postMessage(BufferedReader out, Headers headers, StringBuilder msgBuffer) throws IOException {

	    	int contentLength = headers.contentLength;
	            char[] buffer = new char[contentLength];
	            int bytesRead = 0;
	            
	            while (bytesRead < contentLength) {
	                int readResult = out.read(buffer, bytesRead, contentLength - bytesRead);
	                if (readResult == -1)
	                    throw new IOException("Unexpected end of message");
	                bytesRead += readResult;
	            }
	            msgBuffer.append(CRLF).append(CRLF).append(new String(buffer));
	            LOG.info("LSP sends " + msgBuffer.toString());
	            remote.sendText(msgBuffer.toString());
	            
	    }

		public void run () {
			//StringBuffer message = new StringBuffer();
			Thread thisThread = Thread.currentThread();
			LOG.info("LSP4J: Listening...");
			keepRunning = true;
	        StringBuilder headerBuilder = null;
	        StringBuilder debugBuilder = null;
	        boolean newLine = false;
	        Headers headers = new Headers();
			
			while(keepRunning && !thisThread.isInterrupted() ) {
	            try {
	                int c = out.read();
	                if (c == -1) {
	                    // End of input stream has been reached
	                	try {
	                		Thread.sleep(100);
	                	} catch ( InterruptedException e ) { continue; }
	                } else {
	                    if (debugBuilder == null)
	                        debugBuilder = new StringBuilder();
	                    debugBuilder.append((char) c);
	                    if (c == '\n') {
	                    	LOG.info(">>OUT: " + debugBuilder.toString());
	                    	if ( headerBuilder != null && headerBuilder.toString().startsWith("SLF4J:")) {
	                            fireError(new IllegalStateException(headerBuilder.toString()));
	                    		// Skip and reset
	                            newLine = false;
	                            headerBuilder = null;
	                            debugBuilder = null;
	                            continue;
	                    	}
	                        if (newLine) {
	                            // Two consecutive newlines have been read, which signals the start of the message content
	                            if (headers.contentLength < 0) {
	                                fireError(new IllegalStateException(
	                                    "Missing header " + CONTENT_LENGTH_HEADER + " in input \"" + debugBuilder + "\""
	                                ));
	                            } else {
	                            	postMessage(out, headers, headerBuilder);
	                                newLine = false;
	                                headerBuilder = null;
	                            }
	                            headers = new Headers();
	                            debugBuilder = null;
	                        } else if (headerBuilder != null) {
	                            // A single newline ends a header line
	                            parseHeader(headerBuilder.toString(), headers);
	                            //headerBuilder = null;
	                        }
	                        newLine = true;
	                    } else if (c != '\r') {
	                        // Add the input to the current header line
	                        if (headerBuilder == null)
	                            headerBuilder = new StringBuilder();
	                        headerBuilder.append((char) c);
	                        newLine = false;
	                    }
	                }
	            } catch (IOException e ) {
	                LOG.severe("Out stream handler error: " + e.toString());
                    keepRunning = false;
	            }
				
			}

		}

	};
	
	private static class LogStreamHandler extends Thread {
		private final BufferedReader log;

		public LogStreamHandler(InputStream log) {
			this.log = new BufferedReader(new InputStreamReader(log));
		}
		
		public void run() {
			//StringBuffer message = new StringBuffer();
			Thread thisThread = Thread.currentThread();
			LOG.info("LSP4J: Listening for log...");
			StringBuilder logBuilder = null;
			boolean keepRunning = true;
	        while(keepRunning && !thisThread.isInterrupted() ) {
	            try {
	                int c = log.read();
	                if (c == -1)
	                    // End of input stream has been reached
	                	try {
	                		Thread.sleep(100);
	                	} catch ( InterruptedException e ) { continue; }
	                else {
	                	 if ( logBuilder == null ) logBuilder = new StringBuilder();
	                	 logBuilder.append(c);
	                	 if ( c == '\n' ) {
	                		 LOG.info(logBuilder.toString());
	                		 logBuilder = null;
	                	 }
	                }
	            } catch (IOException ioex) {
	            	LOG.warning("Log stream error: " + ioex.toString());
	            	return;
	            }
	        }
			
		}
		
	}
	
	
	OutputStreamHandler outputHandler = null;
	private int socketIn;
	private int socketOut;
	private String pipeIn;
	private String pipeOut;
	private String debugClient;
	
	public LanguageServerWSEndPoint() {
		super();
		String cfEnvStr = System.getenv(ENV_IPC);
		LOG.info("Environment IPC: " + cfEnvStr);
		JsonReader envReader = Json.createReader(new ByteArrayInputStream(cfEnvStr.getBytes(StandardCharsets.UTF_8)));
		JsonObject cfEnv = envReader.readObject();
		if ( cfEnv.containsKey(ENV_IPC_SOCKET) ) {
			this.ipc = IPC.SOCKET;
			socketEnv(cfEnv.getJsonObject(ENV_IPC_SOCKET));
		} else if ( cfEnv.containsKey(ENV_IPC_PIPES)) {
			this.ipc = IPC.NAMEDPIPES;
			pipeEnv(cfEnv.getJsonObject(ENV_IPC_PIPES));
		} else {
			this.ipc = IPC.STREAM;
			LOG.info("Default Std Stream IPC");
		}
		
		launcherScript = BASE_DIR + System.getenv(ENV_LAUNCHER);
		debugClient = System.getenv(DEBUG_CLIENT);
		LOG.info("Environment launcher: " + launcherScript + " debug client " + debugClient);
		
	}
	
	private void pipeEnv(JsonObject jsonObject) {
		this.pipeIn = jsonObject.getJsonString(IPC_IN).getString();
		this.pipeOut = jsonObject.getJsonString(IPC_OUT).getString();
		LOG.info(String.format("Named pipe IPC: in %s out %s",this.pipeIn, this.pipeOut));
	}

	private void socketEnv(JsonObject jsonObject) {
		this.socketIn = jsonObject.getJsonNumber(IPC_IN).intValue();
		this.socketOut = jsonObject.getJsonNumber(IPC_OUT).intValue();
		LOG.info(String.format("Socket IPC: in %d out %d",this.socketIn, this.socketOut));
	}

	@OnOpen
	public void onOpen(Session session, EndpointConfig endpointConfig) {
		LOG.info("LSP4J: OnOpen is invoked");
		if ( !launcherScript.endsWith(".sh") ) {
			LOG.warning("No launcher script configured");
			return;
		}
		
		this.config = endpointConfig;
        RemoteEndpoint.Basic remoteEndpointBasic = session.getBasicRemote();

	    // Check for already opened
        // Re-open socket causes stopping of LSP binary and re-start
        if ( process != null ) {
        	cleanup();
        }
        

 
        String jdtDirectory = System.getenv("HOME") + "/.java-buildpack/language_server_bin_exec_jdt";
        LOG.info("Working dir is " + jdtDirectory);
		ProcessBuilder pb = new ProcessBuilder("/bin/bash",launcherScript);
		pb.directory(new File(jdtDirectory));
		pb.redirectErrorStream(true);
		
		 //File log = new File(BASE_DIR + "language_server_bin_exec_jdt/lsp.log");
		 //pb.redirectOutput(Redirect.appendTo(log));
		
		Thread openCommunication = null;
		
		Map<String,String> env = pb.environment();
		env.put("JAVA_HOME", System.getProperty("java.home"));
		LOG.info("JAVA_HOME " + System.getProperty("java.home"));
		
		if ( debugClient != null )  env.put("DEBUGCLIENT", debugClient);
		
//		env.put("STDIN_PORT", "8991");
//		env.put("STDOUT_PORT", "8990");
		
		switch (this.ipc) {
			case SOCKET:
		        LOG.info("Using Socket for communication");
		        
		        try {
		        	serverSocketIn = new ServerSocket(this.socketIn);
		        	serverSocketOut = new ServerSocket(this.socketOut);
		    		env.put("STDOUT_PORT", Integer.toString(this.socketIn));
		    		env.put("STDIN_PORT", Integer.toString(this.socketOut));
		        } catch (IOException ex ) {
		        	LOG.warning("Error in Sokcet communication " + ex.toString());
		        }
		        openCommunication  = new Thread( new Runnable () {

					@Override
					public void run() {
						try {
							Socket sin =  serverSocketIn.accept();
							Socket sout = serverSocketOut.accept();
					        
					        inWriter = new PrintWriter(new BufferedWriter( new OutputStreamWriter( sin.getOutputStream() )));
					        out = new InputStreamReader( sout.getInputStream());
						} catch (IOException ex) {
							LOG.warning("Error in Socket communication " + ex.toString());
						}
					}

		        } );
		        openCommunication.start();
		        break;
		        
			case NAMEDPIPES:
		        LOG.info("Using named pipes communication");
		        String processIn = this.pipeIn;
		        String processOut = this.pipeOut;
	    		env.put("STDOUT_PIPE_NAME", this.pipeIn);
	    		env.put("STDIN_PIPE_NAME", this.pipeOut);
		        
		        openCommunication = new Thread(new Runnable() {

					@Override
					public void run() {
				        try {
				        	inWriter = new PrintWriter((new BufferedWriter(new FileWriter(processOut))));
				        	out = new FileReader(processIn);
				        } catch (IOException pipeEx) {
				        	LOG.warning("Error in pipes communication " + pipeEx.toString());
				        }
					}
		        	
		        });
		        
		        // Create pipes
		        try {
			        Process mkfifoProc =new ProcessBuilder("mkfifo", processIn).inheritIO().start();
			        mkfifoProc.waitFor();
			        mkfifoProc = new ProcessBuilder("mkfifo", processOut).inheritIO().start();
			        mkfifoProc.waitFor();
		        } catch ( IOException | InterruptedException mkfifoEx) {
		        	LOG.severe("Pipe error: " + mkfifoEx.getMessage());
		        }
		        openCommunication.start();
		        break;

			case STREAM:
				LOG.info("Using StdIn / StdOut streams");
		}
		
		try {

			process = pb.start();
			LOG.info("Starting....");
			if ( process.isAlive() ) {
		        if ( openCommunication != null ) {
		        	// Either Named pipes or Socket
		        	openCommunication.join();
		        	// TODO LOG output and err
		        	(new LogStreamHandler(process.getInputStream())).start();
		        	switch ( this.ipc) {
		        	case SOCKET:
			        	LOG.info("SocketIn " + this.serverSocketIn.toString() + " stat " + this.serverSocketIn.isBound() );
			        	LOG.info("SocketOut " + this.serverSocketOut.toString() + " stat " + this.serverSocketOut.isBound());
			        	break;
		        	case NAMEDPIPES:
		        		LOG.info("PipeIn exists " + new File(this.pipeIn).exists());
		        		LOG.info("PipeOut exists " + new File(this.pipeIn).exists());
		        		break;
		        	default:
		        		break;
		        	}
		        	
		        } else {
		        	// Stdin / Stdout
					out = new InputStreamReader(process.getInputStream());
					OutputStream in = process.getOutputStream();
					inWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(in)));
		        }
				InputStream er = process.getErrorStream();
				LOG.info("LSP4J: JDT started");

				outputHandler = new OutputStreamHandler(remoteEndpointBasic, new BufferedReader(out) );
				outputHandler.start();
				
			} else {
				LOG.severe("LSP4J: JDT start failure");
			}
	        

		} catch ( InterruptedException | IOException e1) {
			//e1.printStackTrace();
			LOG.severe("IO Exception while starting: " + e1.toString());
		}
		
	}

	@OnMessage
	public void onMessage(String message) {
		LOG.info("InMessage \n" + message);
		if(!process.isAlive()) { LOG.warning("JDT is down"); return; }
		inWriter.write(message);
		inWriter.flush();
	}
	
	@OnClose
	public void onClose(Session session, CloseReason reason ) {
		LOG.info("LSP4J: OnClose is invoked");
		cleanup();

	}	
	
	@OnError
	public void onError(Session session, Throwable thr) {
		LOG.severe("On Error: " + thr.getMessage() + "\n" + thr.toString());
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		// TODO Auto-generated method stub

	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		servletContext = sce.getServletContext();

	}
	
	private void cleanup() {

		outputHandler.interrupt();
		try {
			if ( this.serverSocketIn != null ) {
				this.serverSocketIn.close();
			}
			if ( this.serverSocketOut != null ) {
				this.serverSocketOut.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if ( process != null ) { 
			if ( process.isAlive() ) 
				process.destroyForcibly();
			process = null;
		}

	}
	

}
