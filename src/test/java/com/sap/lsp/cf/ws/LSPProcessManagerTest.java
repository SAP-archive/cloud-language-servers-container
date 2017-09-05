package com.sap.lsp.cf.ws;

import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LSPProcessManagerTest {
	private static final Logger LOG = Logger.getLogger(LSPProcessManagerTest.class.getName());
	private static LSPProcessManager cut;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		LSPEndPointTestUtil testUtil = new LSPEndPointTestUtil();
		String log = testUtil.createInfra();
		LOG.info(log);
		testUtil.MockServerContext();

		cut = new LSPProcessManager(testUtil.getCtx());
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testLSPProcessManager() {
		assertNotNull(cut);
	}

	@Test
	public void testCreateProcess() throws LSPException {
		LSPProcess lsp;
		lsp = cut.createProcess("testWS", "aLang", null);
		assertNotNull("Process createion failed", lsp);
		assertEquals("Wrong project path", "/", lsp.getProjPath());
	}

	public void testCreateProcessP() throws LSPException {
		LSPProcess lsp;
		lsp = cut.createProcess("testWS~myProj", "aLang", null);
		assertNotNull("Process createion failed", lsp);
		assertEquals("Wrong project path", "/myProj/", lsp.getProjPath());
	}

	public void testCreateProcessM() throws LSPException {
		LSPProcess lsp;
		lsp = cut.createProcess("testWS~myProj~myModule", "aLang", null);
		assertNotNull("Process createion failed", lsp);
		assertEquals("Wrong project path", "/myProj/myModule/", lsp.getProjPath());
	}

	@Test
	public void testGetProcess() throws LSPException {
		cut.createProcess("testWS", "aLang", null);
		LSPProcess lsp = cut.getProcess(LSPProcessManager.processKey("testWS", "aLang"));
		assertNotNull(lsp);
	}

	@Test
	public void testGetProcessP() throws LSPException {
		cut.createProcess("testWS~myProj", "aLang", null);
		LSPProcess lsp = cut.getProcess(LSPProcessManager.processKey("testWS~myProj", "aLang"));
		assertNotNull(lsp);
	}

	@Test
	public void testGetProcessM() throws LSPException {
		cut.createProcess("testWS~myProj~myModule", "aLang", null);
		LSPProcess lsp = cut.getProcess(LSPProcessManager.processKey("testWS~myProj~myModule", "aLang"));
		assertNotNull(lsp);
	}

}
