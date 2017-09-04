package com.sap.lsp.cf.ws;

/**
 * Exposes the time passed since last user activity
 */
class IdleTimeHolder {
	private long lastTimestamp = 0;

	private static IdleTimeHolder idleTimeHolder = new IdleTimeHolder();

	static IdleTimeHolder getInstance() {
		return idleTimeHolder;
	}

	void registerUserActivity() {
		lastTimestamp = System.currentTimeMillis();
	}

	long getIdleTime() {
		return System.currentTimeMillis() - lastTimestamp;
	}

}
