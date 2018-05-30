package com.company.mta.java;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import org.junit.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.company.mta.java.HelloServlet;

/**
 * Unit tests for HelloServlet.
 */
public class TestsUnitHelloServlet {

	private static HelloServlet servlet;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		servlet = new HelloServlet();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDoGet() throws ServletException, IOException {
		HttpServletRequest requestMock = mock(HttpServletRequest.class);

		HttpServletResponse responseMock = mock(HttpServletResponse.class);
		ServletOutputStream responseOutput = mock(ServletOutputStream.class);
		doReturn(responseOutput).when(responseMock).getOutputStream();

		servlet.doGet(requestMock, responseMock);

		verify(responseOutput, times(1)).write(any(), anyInt(), anyInt());
	}
}
