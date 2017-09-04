package com.sap.lsp.cf.ws;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(description = "Idle Time", urlPatterns = {"/IdleTime/*"})
public class IdleTime extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try (PrintWriter writer = response.getWriter()) {
            writer.write(Long.toString(IdleTimeHolder.getInstance().getIdleTime()));
        }
    }
}
