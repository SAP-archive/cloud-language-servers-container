package com.sap.lsp.cf.ws;

import java.util.Date;

import javax.servlet.annotation.WebServlet;
import javax.ws.rs.POST;

@WebServlet(description = "Update token ", urlPatterns = { "/UpdateToken/*" })
public class WebsocketToken {

	@POST
	public void update(String token, Date timestamp) {
		WebsocketTokenValidator.getInstance().update(token, timestamp);
	}
}
