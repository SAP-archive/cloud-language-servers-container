package com.sap.lsp.cf.ws;

/**
 * Exposes the time passed since last user activity
 */
public class IdleTimeHolder {
	private long lastTimestamp = 0;

	private static IdleTimeHolder idleTimeHolder = new IdleTimeHolder();

	public static IdleTimeHolder getInstance() {
		return idleTimeHolder;
	}

	public void registerUserActivity() {
		lastTimestamp = System.currentTimeMillis();
	}

	public long getIdleTime() {
		return System.currentTimeMillis() - lastTimestamp;
	}

}
