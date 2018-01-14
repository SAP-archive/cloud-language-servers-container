package com.sap.lsp.cf.ws;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class LangServerCtxTest {

	private static LangServerCtx cut;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		cut = new LangServerCtx("aLang");
		cut.setBaseDir(System.getProperty("user.dir") + File.separator + "src" + File.separator + "test");
		cut.put("workdir", File.separator + "util");
		cut.put("exec", File.separator + "util" + File.separator + "EchoLauncher1.sh");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testLangServerCtx() {
		assertNotNull("Constructor error", cut);
	}

	@Test
	public void testGetProcessBuilder() throws LSPException {
		String[] wsElem = { "ws" };
		ProcessBuilder pb;
		pb = cut.getProcessBuilder(wsElem);
		assertCommandLine(pb, " ws");
	}

	@Test
	public void testGetProcessBuilderP() throws LSPException {
		String[] wsElem = { "ws", "myProj" };
		ProcessBuilder pb;
		pb = cut.getProcessBuilder(wsElem);
		assertCommandLine(pb, " ws myProj");
	}

	@Test
	public void testGetProcessBuilderM() throws LSPException {
		String[] wsElem = { "ws", "myProj", "myModule" };
		ProcessBuilder pb;
		pb = cut.getProcessBuilder(wsElem);
		assertCommandLine(pb, " ws myProj myModule");
	}

	@Test
	public void testGetRpcType() {
		// fail("Not yet implemented");
	}

	private void assertCommandLine(ProcessBuilder pb, String expectParams) {
		String exec = cut.getBaseDir() + cut.get("exec");
		if (System.getProperty("os.name").substring(0, 3).equalsIgnoreCase("win")) {
			assertEquals("cmd.exe /C " + exec + expectParams, String.join(" ", pb.command()));
		} else {
			assertEquals("/bin/bash " + exec + expectParams, String.join(" ", pb.command()));
		}

	}

}
