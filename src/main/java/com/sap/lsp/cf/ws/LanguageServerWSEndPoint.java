package com.sap.lsp.cf.ws;

import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@WebListener
@ServerEndpoint(value="/LanguageServer/{ws}/{lang}", subprotocols={"access_token","local_access"}, configurator = GetHttpSessionConfigurator.class)
public class LanguageServerWSEndPoint implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(LanguageServerWSEndPoint.class.getName());
    private static final String ENV_LSP_SERVERS = "lspservers";
	private static final String LANG_CONTEXT = "langContext";
	private static final String LANG_SRV_PROCESS = "langServerProc";

	private static Map<String,LangServerCtx> langContexts = new HashMap<>();
	private static final LSPProcessManager procManager = new LSPProcessManager(langContexts);

	static {
		if ( System.getenv().containsKey(ENV_LSP_SERVERS)) {
			String langs[] = System.getenv(ENV_LSP_SERVERS).split(",");
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
            final String allowedPattern = "[0-9A-Za-z@.-~]+";
            if (!Pattern.matches(allowedPattern, ws)) {
                LOG.severe("LSP: unsupported special characters in workspace argument");
                session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "workspace is invalid"));
                return;
            }
            if (!Pattern.matches(allowedPattern, lang)) {
                LOG.severe("LSP: unsupported special characters in language argument");
                session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "language is invalid"));
                return;
            }
            String subProtocol = session.getNegotiatedSubprotocol();
            if (subProtocol == null) {
                LOG.severe("LSP: sub-protocol is required for authentication");
                session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "sub-protocol is required for authentication"));
                return;
            }

            Map<String, List<String>> reqParam = session.getRequestParameterMap();
            if (reqParam != null && reqParam.containsKey("local")) {
                return;
            }
            LOG.info("LSP4J: OnOpen is invoked for sub-protocol " + subProtocol);

            @SuppressWarnings("unchecked")
            List<String> requestedProtocols = (List<String>) endpointConfig.getUserProperties()
                    .get(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL);
            if (requestedProtocols != null && requestedProtocols.size() > 0) {
                String secWSToken = requestedProtocols.get(0);
                if (secWSToken.startsWith(subProtocol) && secWSToken.contains(",")) {
                    try {
                        secWSToken = URLDecoder.decode((secWSToken.split(",")[1]).trim(), "UTF-8");
                        if (!validateWSSecurityToken(secWSToken)) {
                            LOG.severe("SECURITY TOKEN is invalid ");
                            session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Security token is invalid"));
                            return;
                        }
                    } catch (UnsupportedEncodingException e) {
                        LOG.severe("SUB-PROTOCOL error " + e.getMessage());
                        session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Sub-protocol error"));
                        return;
                    }
                    LOG.info("Security Token " + secWSToken);
                } else {
                    LOG.severe("SUB-PROTOCOL error ");
                    session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Sub-protocol error"));
                    return;
                }
            } else {
                LOG.severe("SUB-PROTOCOL error ");
                session.close(new CloseReason(CloseCodes.CANNOT_ACCEPT, "Sub-protocol error"));
                return;
            }
        } catch (IOException closeErr) {
            // TODO Auto-generated catch block
            LOG.severe("FATAL ERROR " + closeErr.getMessage());
            return;
        }
        LOG.info(String.format("LSP: create Head Process for lang %s session %s", lang, session.getId()));

        // set timeout
        session.setMaxIdleTimeout(70000L);

        RemoteEndpoint.Basic remoteEndpointBasic = session.getBasicRemote();

       try {
            try {
                LSPProcess process = procManager.createProcess(ws, lang, remoteEndpointBasic, session.getId());

                process.run();
                session.getUserProperties().put(LANG_CONTEXT, langContexts.get(lang));
                session.getUserProperties().put(LANG_SRV_PROCESS, process);
                registerWSSyncListener(LSPProcessManager.processKey(process.getProjPath(), lang),  "/" + ws + "/" + lang,true);
                informReady(remoteEndpointBasic, true);
            } catch (LSPException e) {
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
		LOG.info(String.format("LSP: get Head Process for wsKey %s lang %s session %s", ws, lang, session.getId()));
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
		registerWSSyncListener(LSPProcessManager.processKey(procManager.getProcess(LSPProcessManager.processKey(ws, lang)).getProjPath(), lang),  "/" + ws + "/" + lang,false);
		procManager.cleanProcess(ws, lang, session.getId());
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


	private static final String DI_TOKEN_ENV = "DiToken";

	private void registerWSSyncListener(String procKey, String listenerPath, boolean onOff) {
		String diToken = System.getenv(DI_TOKEN_ENV);
		if (diToken == null) {
			LOG.log(Level.WARNING, "WS notification listener registration skipped - missed Token");
			diToken = "";
		}
        try (CloseableHttpClient httpClient = HttpClients.createSystem()) {
        	assert procKey != null;
        	assert listenerPath != null;
            HttpPost post = new HttpPost("http://localhost:8080/WSSynchronization");
            post.addHeader("Register-lsp", onOff ? "true" : "false");
            post.addHeader(DI_TOKEN_ENV, diToken);
            post.setEntity(new UrlEncodedFormEntity(
                    Collections.singletonList(new BasicNameValuePair(procKey, listenerPath))));
            // Create a custom response handler
            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                	LOG.info("WS Notification listener registration OK: " + status);
                    HttpEntity entity = response.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                } else {
                	LOG.severe("WS Notification listener registration error: " + status);
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            };
            LOG.info("LSP notification registration sending to " + post.getRequestLine().toString() + " with token " + diToken);
            String responseBody = httpClient.execute(post, responseHandler);
        } catch (IOException ex) {
            LOG.severe("WS Notification listener registration error: " + ex.getMessage());
       }

	}

	private boolean validateWSSecurityToken(String token) {
        Date date = new Date();
        Date expirationDate = new Date(Long.valueOf(System.getProperty("com.sap.lsp.cf.ws.expirationDate")));
        return (token.equals(System.getProperty("com.sap.lsp.cf.ws.token")) && date.before(expirationDate));
    }



}
