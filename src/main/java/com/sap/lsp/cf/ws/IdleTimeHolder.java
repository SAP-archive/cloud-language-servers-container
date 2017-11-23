package com.sap.lsp.cf.ws;

/**
 * Exposes the time passed since last user activity
 */
<<<<<<< Updated upstream
public class IdleTimeHolder {
	private long lastTimestamp = 0;
=======
class IdleTimeHolder {
	private long lastTimestamp = System.currentTimeMillis();
>>>>>>> Stashed changes

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
