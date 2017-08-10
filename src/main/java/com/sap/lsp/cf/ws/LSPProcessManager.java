package com.sap.lsp.cf.ws;

import javax.websocket.RemoteEndpoint;
import javax.websocket.RemoteEndpoint.Basic;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class LSPProcessManager {

	private static final String ENV_IPC_SOCKET = "socket";
	private static final String ENV_IPC_PIPES = "pipes";
	private static final String ENV_IPC_CLIENT = "socket-client";
	
	private static final String ENV_IPC_IN_PORT = "inport";
	private static final String ENV_IPC_OUT_PORT = "outport";
	private static final String ENV_PIPE_IN = "pipein";
	private static final String ENV_IPC_CLIENT_PORT = "clientport";
	
	private static final String WSKEY_DELIMITER = "~";


//	public static final String DEBUG_CLIENT = "debugclient";
	
	private static final Logger LOG = Logger.getLogger(LSPProcessManager.class.getName());

	void cleanProcess(String ws, String lang) {
		LSPProcess lspProc = lspProcesses.remove(LSPProcessManager.processKey(ws, lang));
		lspProc.cleanup();
		
	}

	private static class OutputStreamHandler extends Thread {

		public static final String CONTENT_LENGTH_HEADER = "Content-Length";
		public static final String CONTENT_TYPE_HEADER = "Content-Type";
		public static final String CRLF = "\r\n";

		private final Basic remote;
		private final BufferedReader out;
		private boolean keepRunning;

		private static class Headers {
			int contentLength = -1;
			String charset = StandardCharsets.UTF_8.name();
		}

		public OutputStreamHandler(RemoteEndpoint.Basic remoteEndpointBasic, BufferedReader out) {
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

		public void run() {
			LOG.info("LSP: Listening...");
			keepRunning = true;
			StringBuilder headerBuilder = null;
			StringBuilder debugBuilder = null;
			boolean newLine = false;
			Headers headers = new Headers();

			while (keepRunning) {
				try {
					int c = out.read();
					if (c == -1) {
						// End of input stream has been reached
						Thread.sleep(100);
					} else {
						if (debugBuilder == null)
							debugBuilder = new StringBuilder();
						debugBuilder.append((char) c);
						if (c == '\n') {
							LOG.info(">>OUT: " + debugBuilder.toString());
							if (headerBuilder != null && headerBuilder.toString().startsWith("SLF4J:")) {
								fireError(new IllegalStateException(headerBuilder.toString()));
								// Skip and reset
								newLine = false;
								headerBuilder = null;
								debugBuilder = null;
								continue;
							}
							if (newLine) {
								// Two consecutive newlines have been read,
								// which signals the start of the message
								// content
								if (headers.contentLength < 0) {
									fireError(new IllegalStateException("Missing header " + CONTENT_LENGTH_HEADER
											+ " in input \"" + debugBuilder + "\""));
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
								// headerBuilder = null;
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
				} catch (InterruptedException | IOException e) {
					LOG.severe("Out stream handler error: " + e.toString());
					keepRunning = false;
				}

			}

		}

	};

	/**
	 * Responsible for putting the LSP process logs in the standard server log
	 */
	private static class LogStreamHandler extends Thread {
		private final BufferedReader log;

		public LogStreamHandler(InputStream log) {
			this.log = new BufferedReader(new InputStreamReader(log));
		}

		public void run() {
			LOG.info("LSP: Listening for log...");
			StringBuilder logBuilder = null;
			boolean keepRunning = true;
			while (keepRunning) {
				try {
					int c = log.read();
					if (c == -1)
						// End of input stream has been reached
						Thread.sleep(100);
					else {
						if (logBuilder == null)
							logBuilder = new StringBuilder();
						logBuilder.append((char) c);
						if (c == '\n') {
							//LOG.info(">>LOG" + logBuilder.toString());
							logBuilder = null;
						}
					}
				} catch (InterruptedException | IOException e) {
					LOG.warning("Log error: " + e.toString());
					keepRunning = false;
				}
			}
		}

	}

	public static class LSPProcess {
		private enum IPC {
			SOCKET, NAMEDPIPES, STREAM, CLIENTSOCKET
		};

		private IPC ipc = null;
		ServerSocket serverSocketIn = null;
		ServerSocket serverSocketOut = null;
		Socket clientSocket = null;
		private Thread openCommunication = null;

		OutputStreamHandler outputHandler = null;
		LogStreamHandler logHandler = null; 
		private int socketIn = 0;
		private int socketOut = 0;
		private int clientSocketPort = 0;
		private String pipeIn = null;
		private String pipeOut = null;
		private PrintWriter inWriter = null;
		private Reader out = null;
		
		private Process process;
		private ProcessBuilder pb;
		private RemoteEndpoint.Basic remoteEndpoint = null;
		private String projPathElem = ""; 

;
		
		public LSPProcess(String wsKeyElem[], ProcessBuilder pb, Basic remoteEndpoint) {
			this.pb = pb;
			this.remoteEndpoint = remoteEndpoint; 
			this.projPathElem = "/" + String.join("/", Arrays.copyOfRange(wsKeyElem,1,wsKeyElem.length));
		}


		/**
		 * @throws LSPException
		 */
		public void run() throws LSPException {
			switch (this.ipc) {
			case SOCKET:
				LOG.info("Using Socket for communication");

				try {
					serverSocketIn = new ServerSocket(this.socketIn);
					serverSocketOut = new ServerSocket(this.socketOut);
				} catch (IOException ex) {
					LOG.warning("Error in Socket communication " + ex.toString());
					throw new LSPException(ex);
				}
				openCommunication = new Thread(new Runnable() {

					@Override
					public void run() {
						try {
							Socket sin = serverSocketIn.accept();
							Socket sout = serverSocketOut.accept();

							inWriter = new PrintWriter(
									new BufferedWriter(new OutputStreamWriter(sin.getOutputStream())));
							out = new InputStreamReader(sout.getInputStream());
						} catch (IOException ex) {
							LOG.warning("Error in Socket communication " + ex.toString());
						}
					}

				});
				openCommunication.start();
				break;

			case NAMEDPIPES:
				LOG.info("Using named pipes communication");
				String processIn = this.pipeIn;
				String processOut = this.pipeOut;

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
					Process mkfifoProc = new ProcessBuilder("mkfifo", processIn).inheritIO().start();
					mkfifoProc.waitFor();
					mkfifoProc = new ProcessBuilder("mkfifo", processOut).inheritIO().start();
					mkfifoProc.waitFor();
				} catch (IOException | InterruptedException mkfifoEx) {
					LOG.severe("Pipe error: " + mkfifoEx.getMessage());
				}
				openCommunication.start();
				break;

			case STREAM:
				LOG.info("Using StdIn / StdOut streams");
			}
			
			try {
				process = pb.start();
				LOG.info("LSP Starting....");
				if ( process.isAlive() ) {
			        if ( openCommunication != null ) {
			        	// Either Named pipes or Socket
			        	openCommunication.join(30000L);
			        	// TODO LOG output and err
			        	logHandler = new LogStreamHandler(process.getInputStream());
			        	logHandler.start();
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
			        } else if (this.ipc == IPC.CLIENTSOCKET ) {
			        	LOG.info(String.format("LSP: attach to server %s port %d", InetAddress.getLoopbackAddress().getHostName(), clientSocketPort));
			        	Thread.sleep(500L);
			        	this.clientSocket = new Socket(/*InetAddress.getLoopbackAddress()*/  "localhost",clientSocketPort);
				        inWriter = new PrintWriter(new BufferedWriter( new OutputStreamWriter( this.clientSocket.getOutputStream() )));
				        out = new InputStreamReader( this.clientSocket.getInputStream());		        	
			        } else {
			        	// Stdin / Stdout
						out = new InputStreamReader(process.getInputStream());
						OutputStream in = process.getOutputStream();
						inWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(in)));
			        }
					InputStream er = process.getErrorStream();
					LOG.info("LSP: Server started");

					outputHandler = new OutputStreamHandler(remoteEndpoint, new BufferedReader(out) );
					outputHandler.start();
//					informReady(remoteEndpointBasic,true);

				} else {
					LOG.severe("LSP: Server start failure");
					throw new LSPException();
//					informReady(remoteEndpointBasic,false);
				}


			} catch ( InterruptedException | IOException e1) {
				//e1.printStackTrace();
				LOG.severe("IO Exception while starting: " + e1.toString());
			}
			

		}
		
		void cleanup() {
			LOG.info("process cleanup");
			if (outputHandler != null && outputHandler.isAlive()) outputHandler.interrupt();
			if (logHandler != null && logHandler.isAlive() ) logHandler.interrupt();

			// !! Process must be closed before closing the LSP out reader otherwise the out reader is stuck on close!!!
			if ( process != null ) {

				if ( process.isAlive() )
					process.destroyForcibly();
				process = null;
			}

			try {
				if ( inWriter != null ) inWriter.close();
				if ( out != null ) out.close();
				
				if ( this.serverSocketIn != null && !this.serverSocketIn.isClosed()) {
					this.serverSocketIn.close();
				}
				if ( this.serverSocketOut != null && !this.serverSocketOut.isClosed()) {
					this.serverSocketOut.close();
				}
				if ( this.clientSocket != null && !this.clientSocket.isClosed()) {
					this.clientSocket.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		public synchronized void enqueueCall(String message) {
			if(process == null || !process.isAlive() || inWriter == null) { LOG.warning("JDT is down"); return; }
			inWriter.write(message);
			inWriter.flush();
		}


		public void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC socket, int in, int out) {
			this.ipc = socket;
			this.socketIn = in;
			this.socketOut = out;
			LOG.info(String.format("Socket IPC: in %d out %d",this.socketIn, this.socketOut));
		}


		public void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC namedpipes, String in, String out) {
			this.ipc = namedpipes;
			this.pipeIn = in;
			this.pipeOut = out;
			LOG.info(String.format("Named pipe IPC: in %s out %s",this.pipeIn, this.pipeOut));
			
		}


		public void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC clientsocket, int port) {
			this.ipc = clientsocket;
			this.clientSocketPort = port;
			LOG.info(String.format("Client socket IPC: %d", this.clientSocketPort));
		}


		public void confIpc(com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess.IPC stream) {
			this.ipc = stream;
			LOG.info(String.format("Stream IPC"));
		}


		public String getProjPath() {
			// TODO Auto-generated method stub
			return this.projPathElem;
		}
		
	}

	private Map<String, LangServerCtx> langContexts;

	public LSPProcessManager(Map<String, LangServerCtx> langContexts) {
		this.langContexts = langContexts;
	}

	private Map<String, LSPProcess> lspProcesses = Collections.synchronizedMap(new HashMap<String, LSPProcess>());

	public synchronized LSPProcess createProcess(String wsKey, String lang, RemoteEndpoint.Basic remoteEndpoint) throws LSPException {

		String procKey = processKey(wsKey,lang);
		String rpcType = langContexts.get(lang).getRpcType();
		String wsKeyElem[] = wsKey.split(WSKEY_DELIMITER,3);
		
		LSPProcess newlsp = new LSPProcess(wsKeyElem, langContexts.get(lang).getProcessBuilder(wsKeyElem), remoteEndpoint);
		switch(rpcType) {
		case ENV_IPC_SOCKET:
			socketEnv(newlsp, LangServerCtx.LangPrefix(lang));
			break;
		case ENV_IPC_PIPES:
			pipeEnv(newlsp, LangServerCtx.LangPrefix(lang));
			break;
		case ENV_IPC_CLIENT:
			clientSocketEnv(newlsp, LangServerCtx.LangPrefix(lang));
			break;
		default:
			streamEnv(newlsp);
		}
		lspProcesses.put(procKey, newlsp);
		return newlsp;
	}

	public static String processKey(String ws, String lang) {
		return ws + ":" + lang;
	}

	public LSPProcess getProcess(String processKey) {
		return lspProcesses.get(processKey);
	}

	private void pipeEnv(LSPProcess newLsp, String prefix) {
		newLsp.confIpc(LSPProcess.IPC.NAMEDPIPES, System.getenv(prefix + ENV_PIPE_IN), System.getenv(prefix + ENV_IPC_OUT_PORT));
		//LOG.info(String.format("Named pipe IPC: in %s out %s",this.pipeIn, this.pipeOut));
	}

	private void socketEnv(LSPProcess newLsp, String prefix) {
		try {
			newLsp.confIpc(LSPProcess.IPC.SOCKET, Integer.parseInt(System.getenv(prefix + ENV_IPC_IN_PORT)), Integer.parseInt(System.getenv(prefix + ENV_IPC_OUT_PORT)));
		//LOG.info(String.format("Socket IPC: in %d out %d",this.socketIn, this.socketOut));
		} catch ( NumberFormatException fe) {
			throw new LSPConfigurationException();
		}
	}

	private void clientSocketEnv(LSPProcess newLsp, String prefix) {
		try {
			newLsp.confIpc(LSPProcess.IPC.CLIENTSOCKET, Integer.parseInt(System.getenv(prefix + ENV_IPC_CLIENT_PORT)));
		//LOG.info(String.format("Client socket IPC: %d", this.clientSocketPort));
		} catch ( NumberFormatException fe) {
			throw new LSPConfigurationException();
		}
	}
	
	private void streamEnv(LSPProcess newLsp) {
		newLsp.confIpc(LSPProcess.IPC.STREAM);
	}

}
