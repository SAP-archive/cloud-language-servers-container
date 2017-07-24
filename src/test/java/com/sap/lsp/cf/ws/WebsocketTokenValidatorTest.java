package com.sap.lsp.cf.ws;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Test;

public class WebsocketTokenValidatorTest {


	@Test
	public void testUpdateTokenInfo() throws Exception {

		WebsocketTokenValidator websocketTokenValidator = WebsocketTokenValidator.getInstance();

		// update token info - value and timestamp
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		Date validDate = sdf.parse("07/07/2018");
		String token = "WebsocketTokenValidatorTestToken";
		websocketTokenValidator.update(token, validDate);

		// test valid inputs
		boolean isValid = websocketTokenValidator.validateToken(token);
		assertTrue(isValid);

		// test invalid token value
		String invalidToken = "WebsocketTokenValidatorTestToken1";
		isValid = websocketTokenValidator.validateToken(invalidToken);
		assertFalse(isValid);

		// test invalid values- token and date
		Date invalidDate = sdf.parse("07/07/2017");
		websocketTokenValidator.update(token, invalidDate);
		isValid = websocketTokenValidator.validateToken(invalidToken);
		assertFalse(isValid);

	}

}
