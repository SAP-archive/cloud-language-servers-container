package com.sap.lsp.cf.ws;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class LangServerCtx extends HashMap<String,String> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6250146579876230161L;
	private static String BASE_DIR = "/home/vcap/app/.java-buildpack/";
	public static final String ENV_LSP_WORKDIR = "workdir"; 
	public static final String ENV_LAUNCHER = "exec";
	public static final String ENV_RPCTYPE = "protocol";

	private static final Logger LOG = Logger.getLogger(LangServerCtx.class.getName());

	public LangServerCtx(String lang) {

		String prefix = LangPrefix(lang);
		System.getenv().forEach((envVar, value)->{
			// JAVA_HOME - Special case
			if ( envVar.startsWith(prefix) && !envVar.equals("JAVA_HOME")) {
				put(envVar.substring(prefix.length(),envVar.length()), value);
			}
		});
		
		// Test hack - override Base Directory for non-CF test
		if ( System.getenv("basedir") != null ) BASE_DIR = System.getenv("basedir");
		
	}
	
	public ProcessBuilder getProcessBuilder(String[] wsKeyElem) throws LSPException {
		File wDir;
		String launcherScript;
		if ( !this.containsKey(ENV_LAUNCHER) || !this.containsKey(ENV_LSP_WORKDIR)) {
			LOG.warning("No work dir or launcher script configured");
			throw new LSPConfigurationException();
		} else {
			launcherScript = BASE_DIR + get(ENV_LAUNCHER);
			if (!(new File(launcherScript).exists()) ) {
				LOG.warning("No launcher script exists " + launcherScript);
				throw new LSPConfigurationException();
			}
			LOG.info("LSP launcher is: " + launcherScript);

			String workdir = BASE_DIR + get(ENV_LSP_WORKDIR);
			wDir = new File(workdir);
			if ( !wDir.exists() || !wDir.isDirectory() ) {
				LOG.warning("No working directory exists");
				throw new LSPConfigurationException();
			}
	        LOG.info("LSP Working dir is " + workdir);
	        LOG.info("LSP Env HOME: " + System.getenv("HOME"));
		}
		

		List<String> cmd = new ArrayList<>();
		String scriptExec = System.getProperty("os.name").substring(0,3).equalsIgnoreCase("win") ? "cmd.exe /C" : "/bin/bash";
		cmd.add(scriptExec);
		cmd.add(launcherScript);
		Collections.addAll(cmd, wsKeyElem);

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(wDir);
		pb.redirectErrorStream(true);
		
		Map<String,String> env = pb.environment();
		//TODO JAVA_HOME is relevant only to JDT. find a way to send it from build pack
		env.put("JAVA_HOME", System.getProperty("java.home"));
		LOG.info("JAVA_HOME " + System.getProperty("java.home"));
		env.putAll(this);

		return pb;
	}

	public String getRpcType() {
		return get(ENV_RPCTYPE);
		
	}
	
	public static String LangPrefix(String lang) {
		return "LSP" + lang.toUpperCase() + "_";
	}

	protected void setBaseDir(String baseDir) {
		BASE_DIR = baseDir;
	}
	
	protected String getBaseDir() {
		return BASE_DIR;
	}
}
