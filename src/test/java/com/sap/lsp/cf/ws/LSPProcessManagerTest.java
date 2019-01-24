package com.sap.lsp.cf.ws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.TemporaryFolder;

public class LSPProcessManagerTest {
	
	private static final String languageId = "aLang";
	
	private static LSPProcessManager lspProcessManager;

	@Rule
	public TemporaryFolder workdir = new TemporaryFolder();
	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Before
	public void setup() throws IOException {
	   workdir.newFile(LangServerCtx.LAUNCHER_FILE_NAME);
	   LangServerCtx aLangCtx = new LangServerCtx(languageId, workdir.getRoot().getPath());
	   aLangCtx.put(LangServerCtx.ENV_RPCTYPE, "stream");
	   Map<String,LangServerCtx> langContexts = Collections.singletonMap(languageId, aLangCtx);
	   lspProcessManager = new LSPProcessManager(langContexts);
    }

	@Test
	public void testLSPProcessManager() {
		assertNotNull(lspProcessManager);
	}

	@Test
	public void testCreateProcess() throws LSPException {
		LSPProcess lspProcess = lspProcessManager.createProcess("testWS", "aLang", null, "1");
		assertNotNull("Process creation failed", lspProcess);
		assertEquals("Wrong project path", "/", lspProcess.getProjPath());
	}

	@Test
	public void testGetProcess() throws LSPException {
		lspProcessManager.createProcess("testWS", "aLang", null, "1");
		LSPProcess lspProcess = lspProcessManager.getProcess(LSPProcessManager.processKey("testWS", "aLang"));
		assertNotNull(lspProcess);
	}

	@Test
	public void testGetProcessP() throws LSPException {
		lspProcessManager.createProcess("testWS~myProj", "aLang", null, "1");
		LSPProcess lspProcess = lspProcessManager.getProcess(LSPProcessManager.processKey("testWS~myProj", "aLang"));
		assertNotNull(lspProcess);
	}

	@Test
	public void testGetProcessM() throws LSPException {
		lspProcessManager.createProcess("testWS~myProj~myModule", "aLang", null, "1");
		LSPProcess lspProcess = lspProcessManager.getProcess(LSPProcessManager.processKey("testWS~myProj~myModule", "aLang"));
		assertNotNull(lspProcess);
	}

}
