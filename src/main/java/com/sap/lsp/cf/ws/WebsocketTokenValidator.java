package com.sap.lsp.cf.ws;

import java.util.Date;

public class WebsocketTokenValidator {

	private String token;
	private Date timestamp;

	private static WebsocketTokenValidator tokenValidator = null;
	
	private WebsocketTokenValidator() {}
	
	public static synchronized WebsocketTokenValidator getInstance() {
		if (tokenValidator == null) {
			tokenValidator = new WebsocketTokenValidator();
		}
		return tokenValidator;
	}

	public boolean validateToken(String token) {
		Date date = new Date();
		return (tokenValidator.token == token && date.before(tokenValidator.timestamp));
	}

	public void update(String token, Date timestamp) {
		tokenValidator.token = token;
		tokenValidator.timestamp = timestamp;
	}

}
