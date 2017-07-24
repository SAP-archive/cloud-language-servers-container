package com.sap.lsp.cf.ws;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.sap.lsp.cf.ws.LSPProcessManager.LSPProcess;

public class LSPProcessManagerTest {
	
	private static final Logger LOG = Logger.getLogger(LSPProcessManagerTest.class.getName());

	private static LSPProcessManager cut;
	private LSPProcessManager.LSPProcess proc;

	private static LSPEndPointTestUtil testUtil;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		
		testUtil = new LSPEndPointTestUtil();
		String log = testUtil.createInfra();
		LOG.info(log);
		testUtil.MockServerContext();		
		
		cut = new LSPProcessManager(testUtil.getCtx());
	}

	
	@After
	public void tearDown() throws Exception {
		if ( proc != null ) cut.cleanProcess("testWS", "aLang");
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}
	

	@Test
	public void testLSPProcessManager() {
		assertNotNull(cut);
	}

	@Test
	public void testCreateProcess() {
		LSPProcess lsp = cut.createProcess("testWS", "aLang", null);
		assertNotNull(lsp);
	}

	@Test
	public void testGetProcess() {
		cut.createProcess("testWS", "aLang", null);
		LSPProcess lsp = cut.getProcess(LSPProcessManager.processKey("testWS", "aLang"));
		assertNotNull(lsp);
	}

}
