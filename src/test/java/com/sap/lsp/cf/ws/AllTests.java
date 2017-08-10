package com.sap.lsp.cf.ws;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ IdleTimeHolderTest.class, LangServerCtxTest.class, LanguageServerWSEndPointTest.class,
		LSPProcessManagerTest.class })

public class AllTests {

}
