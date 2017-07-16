package com.sap.lsp.cf.ws;

import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@WebListener
@ServerEndpoint(value="/LanguageServer/{ws}/{lang}")
public class LanguageServerWSEndPoint implements ServletContextListener {

	public static final String ENV_LSPSERVERS = "lspservers";

	private static final Logger LOG = Logger.getLogger(LanguageServerWSEndPoint.class.getName());
	private static final String LANG_CONTEXT = "langContext";
	private static final String LANG_SRV_PROCESS = "langServerProc";	

	
	private static Map<String,LangServerCtx> langContexts = new HashMap<String,LangServerCtx>();
	
	private static final LSPProcessManager procManager = new LSPProcessManager(langContexts);

	static {
		if ( System.getenv().containsKey(ENV_LSPSERVERS)) {
			String langs[] = System.getenv(ENV_LSPSERVERS).split(",");
			for(String lang : langs) {
				if ( lang.length() > 0) langContexts.put(lang, new LangServerCtx(lang));
			}
		}
	}

	public LanguageServerWSEndPoint() {
		super();		
	}


	@OnOpen
	public void onOpen(@PathParam("ws") String ws, @PathParam("lang") String lang, Session session, EndpointConfig endpointConfig) {
		Map<String,List<String>> reqParam = session.getRequestParameterMap();
		if ( reqParam != null && reqParam.containsKey("local") ) {
			return;
		}
		LOG.info("LSP: OnOpen is invoked");
		LOG.info(String.format("LSP: create Head Process for lang %s session %s", lang, session.getId()));

		// set timeout
		session.setMaxIdleTimeout(0L);

        RemoteEndpoint.Basic remoteEndpointBasic = session.getBasicRemote();
        
        try {

        LSPProcess process = procManager.createProcess(ws, lang, remoteEndpointBasic);

        try {
			process.run();
	        session.getUserProperties().put(LANG_CONTEXT, langContexts.get(lang));
	        session.getUserProperties().put(LANG_SRV_PROCESS, process);
			informReady(remoteEndpointBasic, true);
		} catch (LSPException e) {
			// TODO Auto-generated catch block
			informReady(remoteEndpointBasic, false);
			session.close(new CloseReason(null,"Fatal error"));
		}
        
        } catch (IOException ex) {
        	LOG.severe("LSP open: FATAL exception");
        }

	}

	@OnMessage
	public void onMessage(@PathParam("ws") String ws, @PathParam("lang") String lang, String message, Session session) {
		if ( message.length() == 0 ) return; // This is just ping!
/*		try {
			session.getBasicRemote().sendText("");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		LOG.info("LSP: onMessage is invoked: \n" + message);
		LOG.info(String.format("LSP: get Head Process for lang %s session %s", lang, session.getId()));
		LSPProcess lspProc = procManager.getProcess(LSPProcessManager.processKey(ws, lang));
		lspProc.enqueueCall(message);
	}

	@OnClose
	public void onClose(@PathParam("ws") String ws, @PathParam("lang") String lang, Session session, CloseReason reason ) {
		Map<String,List<String>> reqParam = session.getRequestParameterMap();
		if ( reqParam != null && reqParam.containsKey("local") ) {
			return;
		}
		LOG.info("LSP: OnClose is invoked");
		procManager.cleanProcess(ws, lang);

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
		//servletContext = sce.getServletContext();

	}

/* Private methods *
 * 	
 */
	
	private static final String READY_MESSAGE_HEADER = "Content-Length: %d\r\n\r\n%s";
	private static final String READY_MESSAGE = "{\"jsonrpc\": \"2.0\",\"method\": \"protocol/Ready\"}";
	private static final String ERROR_MESSAGE = "{\"jsonrpc\": \"2.0\",\"method\": \"protocol/Error\"}";
	
	private void informReady(RemoteEndpoint.Basic remote, boolean bReady) throws IOException {
		String msg = bReady ? READY_MESSAGE : ERROR_MESSAGE;
		String readyMsg = String.format(READY_MESSAGE_HEADER,msg.length(),msg);
		
		remote.sendText(readyMsg);
	}


}
