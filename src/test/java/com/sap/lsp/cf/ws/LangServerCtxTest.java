package com.sap.lsp.cf.ws;

import static org.junit.Assert.assertEquals;

import org.apache.commons.io.FilenameUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LangServerCtxTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

  	private LangServerCtx languageServerCtx;

	@Before
	public void setup() throws Exception {
		tempFolder.newFile("launcher.sh");
		languageServerCtx = new LangServerCtx("java", tempFolder.getRoot().getPath());
	}

	@Test
	public void testGetProcessBuilder() throws LSPException {
		String[] wsElem = { "ws" };
		ProcessBuilder pb = languageServerCtx.getProcessBuilder(wsElem);
		assertCommandLine(pb, " ws");
	}

	@Test
	public void testGetProcessBuilderP() throws LSPException {
		String[] wsElem = { "ws", "myProj" };
		ProcessBuilder pb = languageServerCtx.getProcessBuilder(wsElem);
		assertCommandLine(pb, " ws myProj");
	}

	@Test
	public void testGetProcessBuilderM() throws LSPException {
		String[] wsElem = { "ws", "myProj", "myModule" };
		ProcessBuilder pb = languageServerCtx.getProcessBuilder(wsElem);
		assertCommandLine(pb, " ws myProj myModule");
	}

	private void assertCommandLine(ProcessBuilder pb, String expectParams) {
		String LauncherPath = FilenameUtils.concat(tempFolder.getRoot().getPath(), LangServerCtx.LAUNCHER_FILE_NAME);
		if (System.getProperty("os.name").substring(0, 3).equalsIgnoreCase("win")) {
			assertEquals("cmd.exe /C " + LauncherPath + expectParams, String.join(" ", pb.command()));
		} else {
			assertEquals("/bin/bash " + LauncherPath + expectParams, String.join(" ", pb.command()));
		}
	}

}
