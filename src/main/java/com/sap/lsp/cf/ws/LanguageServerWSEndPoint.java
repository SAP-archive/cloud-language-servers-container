package com.sap.lsp.cf.ws;

import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@WebListener
@ServerEndpoint(value="/LanguageServer/{ws}/{lang}", subprotocols={"access_token","local_access"}, configurator = GetHttpSessionConfigurator.class)
public class LanguageServerWSEndPoint implements ServletContextListener {

	public static final String ENV_LSPSERVERS = "lspservers";

	private static final Logger LOG = Logger.getLogger(LanguageServerWSEndPoint.class.getName());
	private static final String LANG_CONTEXT = "langContext";
	private static final String LANG_SRV_PROCESS = "langServerProc";
	private static final String ENV_AUTH = "auth";

	
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
	    try {
            String subprotocol = session.getNegotiatedSubprotocol();
            if (subprotocol == null) {
                LOG.severe("LSP: Subprotocol is required for authentication");
                session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Subprotocol is required for authentication"));
                return;
            }

            Map<String, List<String>> reqParam = session.getRequestParameterMap();
            if (reqParam != null && reqParam.containsKey("local")) {
                return;
            }
            LOG.info("LSP4J: OnOpen is invoked for subprotocol " + subprotocol);

            @SuppressWarnings("unchecked")
            List<String> requestedPotocols = (List<String>) endpointConfig.getUserProperties()
                    .get(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL);
            if (requestedPotocols != null && requestedPotocols.size() >= 0) {
                String secWSToken = requestedPotocols.get(0);
                if (subprotocol != null && secWSToken.startsWith(subprotocol) && secWSToken.contains(",")) {
                    try {
                        secWSToken = URLDecoder.decode((secWSToken.split(",")[1]).trim(), "UTF-8");
                        if (!validateWSSecurityToken(secWSToken)) {
                            LOG.severe("SECURITY TOKEN is invalid ");
                            session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Security token is invalid"));
                            return;
                        }
                    } catch (UnsupportedEncodingException e) {
                        LOG.severe("SUBPROTOCOL error " + e.getMessage());
                        session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Subprotocol error"));
                        return;
                    }
                    LOG.info("Security Token " + secWSToken);
                } else {
                    LOG.severe("SUBPROTOCOL error ");
                    session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Subprotocol error"));
                    return;
                }
            } else {
                LOG.severe("SUBPROTOCOL error ");
                session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Subprotocol error"));
                return;
            }
        } catch (IOException closeErr) {
            // TODO Auto-generated catch block
            LOG.severe("FATAL ERROR " + closeErr.getMessage());
            return;
        }

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
                session.close(new CloseReason(CloseCodes.UNEXPECTED_CONDITION, "Fatal error"));
            }
        } catch (IOException ex) {
            LOG.severe("LSP open: FATAL exception");
        }

    }

	@OnMessage
	public void onMessage(@PathParam("ws") String ws, @PathParam("lang") String lang, String message, Session session) {
		if ( message.length() == 0 ) return; // This is just ping!
		IdleTimeHolder.getInstance().registerUserActivity();
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
	    String message = thr.getMessage() != null ? thr.getMessage() : thr.toString();
		LOG.severe("On Error: " + message);
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
	
	private boolean validateWSSecurityToken(String token) {
        Date date = new Date();
        Date expiratioDate = new Date(Long.valueOf(System.getProperty("com.sap.lsp.cf.ws.expirationDate")));
        return (token.equals(System.getProperty("com.sap.lsp.cf.ws.token")) && date.before(expiratioDate));
    }


}
