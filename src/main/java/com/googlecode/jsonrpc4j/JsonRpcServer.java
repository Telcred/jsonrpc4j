package com.googlecode.jsonrpc4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A JSON-RPC request server reads JSON-RPC requests from an input stream and writes responses to an output stream.
 * Supports handler and servlet requests.
 */
public class JsonRpcServer extends JsonRpcBasicServer {
	private static final Logger logger = LoggerFactory.getLogger(JsonRpcServer.class);

	private String contentType = JSONRPC_CONTENT_TYPE;

	/**
	 * Creates the server with the given {@link ObjectMapper} delegating
	 * all calls to the given {@code handler} {@link Object} but only
	 * methods available on the {@code remoteInterface}.
	 *
	 * @param mapper          the {@link ObjectMapper}
	 * @param handler         the {@code handler}
	 * @param remoteInterface the interface
	 */
	public JsonRpcServer(ObjectMapper mapper, Object handler, Class<?> remoteInterface) {
		super(mapper, handler, remoteInterface);
	}

	/**
	 * Creates the server with the given {@link ObjectMapper} delegating
	 * all calls to the given {@code handler}.
	 *
	 * @param mapper  the {@link ObjectMapper}
	 * @param handler the {@code handler}
	 */
	public JsonRpcServer(ObjectMapper mapper, Object handler) {
		super(mapper, handler, null);
	}

	/**
	 * Creates the server with a default {@link ObjectMapper} delegating
	 * all calls to the given {@code handler} {@link Object} but only
	 * methods available on the {@code remoteInterface}.
	 *
	 * @param handler         the {@code handler}
	 * @param remoteInterface the interface
	 */
	public JsonRpcServer(Object handler, Class<?> remoteInterface) {
		super(new ObjectMapper(), handler, remoteInterface);
	}

	/**
	 * Creates the server with a default {@link ObjectMapper} delegating
	 * all calls to the given {@code handler}.
	 *
	 * @param handler the {@code handler}
	 */
	public JsonRpcServer(Object handler) {
		super(new ObjectMapper(), handler, null);
	}

	/**
	 * Handles a servlet request.
	 *
	 * @param request  the {@link HttpServletRequest}
	 * @param response the {@link HttpServletResponse}
	 * @throws IOException on error
	 */
	public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
		handleCommon(
			new JavaxHttpServletRequest(request),
			new JavaxHttpServletResponse(response)
		);
	}

	private void handleCommon(CommonHttpServletRequest request, CommonHttpServletResponse response) throws IOException {
		logger.debug("Handling HttpServletRequest {}", request.unwrap());
		response.setContentType(contentType);
		OutputStream output = response.getOutputStream();
		InputStream input = getRequestStream(request);
		int result = ErrorResolver.JsonError.PARSE_ERROR.code;

		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		try {
			result = handleRequest(input, byteOutput);
		} catch (Throwable t) {
			if (t instanceof StreamEndedException) {
				logger.debug("Bad request: empty contents!");
			} else {
				logger.error(t.getMessage(), t);
			}
		}

		response.setStatus(resolveHttpStatusCode(result));
		response.setContentLength(byteOutput.size());
		byteOutput.writeTo(output);
		output.flush();
	}

	private int resolveHttpStatusCode(int result) {
		if (this.httpStatusCodeProvider != null) {
			return this.httpStatusCodeProvider.getHttpStatusCode(result);
		} else {
			return DefaultHttpStatusCodeProvider.INSTANCE.getHttpStatusCode(result);
		}
	}

	private InputStream getRequestStream(CommonHttpServletRequest request) throws IOException {
		InputStream input;
		if ("POST".equals(request.getMethod())) {
			input = request.getInputStream();
		} else if ("GET".equals(request.getMethod())) {
			input = createInputStream(request);
		} else {
			throw new IOException("Invalid request method, only POST and GET is supported");
		}
		return input;
	}

	private static InputStream createInputStream(CommonHttpServletRequest request) throws IOException {
		String method = request.getParameter(METHOD);
		String id = request.getParameter(ID);
		String params = request.getParameter(PARAMS);
		if (method == null && id == null && params == null) {
			return new ByteArrayInputStream(new byte[]{});
		} else {
			return createInputStream(method, id, params);
		}
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	private interface CommonHttpServletRequest {
		Object unwrap();
		InputStream getInputStream() throws IOException;
		String getMethod();
		String getParameter(String name);
	}

	private static class JavaxHttpServletRequest implements CommonHttpServletRequest {

		private final HttpServletRequest request;

		private JavaxHttpServletRequest(HttpServletRequest request) {
			this.request = request;
		}

		@Override
		public Object unwrap() {
			return this.request;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return this.request.getInputStream();
		}

		@Override
		public String getMethod() {
			return this.request.getMethod();
		}

		@Override
		public String getParameter(String name) {
			return this.request.getParameter(name);
		}
	}

	private interface CommonHttpServletResponse {
		void setContentType(String type);
		void setStatus(int sc);
		void setContentLength(int len);
		OutputStream getOutputStream() throws IOException;
	}

	private static class JavaxHttpServletResponse implements CommonHttpServletResponse {

		private final HttpServletResponse response;

		private JavaxHttpServletResponse(HttpServletResponse response) {
			this.response = response;
		}

		@Override
		public void setContentType(String type) {
			this.response.setContentType(type);
		}

		@Override
		public void setStatus(int sc) {
			this.response.setStatus(sc);
		}

		@Override
		public void setContentLength(int len) {
			this.response.setContentLength(len);
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return this.response.getOutputStream();
		}
	}
}
