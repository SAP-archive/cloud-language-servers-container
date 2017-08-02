package com.sap.lsp.cf.ws;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;


@WebFilter(filterName = "LanguageServerFilter", urlPatterns = {"/WSSynchronization/*", "/UpdateToken/*"})
public class LanguageServerFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(LanguageServerFilter.class.getName());

    private static final String DI_TOKEN_ENV = "DiToken";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        LOG.info("LSP filter init");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        String requestUuid = extractDIToken(servletRequest);
        if (System.getenv().containsKey(DI_TOKEN_ENV)) {
            String diToken = System.getenv(DI_TOKEN_ENV);
            if (!diToken.equals(requestUuid)) {
                LOG.warning("Invalid request - token mismatch. Got token " + requestUuid);
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
        return ((HttpServletRequest) request).getHeader("DiToken");
    }

}
