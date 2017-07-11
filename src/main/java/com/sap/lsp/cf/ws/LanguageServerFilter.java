package com.sap.lsp.cf.ws;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;


@WebFilter(filterName = "LanguageServerFilter", urlPatterns = {"/WSSynchronization/*"})
public class LanguageServerFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(LanguageServerFilter.class.getName());

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        LOG.info("LSP filter init");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        LOG.info("LSP filter doFilter");
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        String requestUuid = extractDIToken(servletRequest);
        if (System.getenv().containsKey("DiToken")) {
            String diToken = System.getenv("DiToken");
            if (!diToken.equals(requestUuid)) {
                LOG.info("Invalid request - token mismatch.");
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid request - token mismatch");
                return;
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
    private String extractDIToken(ServletRequest request) {
        String diToken = ((HttpServletRequest) request).getHeader("DiToken");
        return diToken;
    }

}
