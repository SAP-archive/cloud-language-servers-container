package com.sap.lsp.cf.ws;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

@WebServlet(description = "Update token ", urlPatterns = {"/UpdateToken/*"})
public class WebsocketToken extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(WebsocketToken.class.getName());

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Long timestamp = Long.parseLong(request.getParameter("expiration"));
        String token = request.getParameter("token");

        LOG.info("UpdateToken was called with token " + token + " expiration date " + timestamp);

        System.setProperty("com.sap.lsp.cf.ws.token", token);
        System.setProperty("com.sap.lsp.cf.ws.expirationDate", timestamp.toString());
    }
}
