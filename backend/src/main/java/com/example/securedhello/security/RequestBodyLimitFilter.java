package com.example.securedhello.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.securedhello.web.ProblemResponses;

/**
 * Rejects request bodies larger than the configured limit with 400 {@code request_too_large}
 * before CSRF, authentication or any controller runs, whatever the transfer encoding.
 * <ul>
 * <li>A declared Content-Length over the limit is refused without reading.</li>
 * <li>A request with neither Content-Length nor Transfer-Encoding has no body and passes
 * untouched.</li>
 * <li>A chunked body is read into memory up to limit + 1 bytes and refused when over the limit;
 * otherwise the chain reads the buffered bytes through {@code getInputStream()} or
 * {@code getReader()}. Form fields in a chunked body are not decoded, so a chunked form post
 * carrying its CSRF token only in the body fails closed with 403. API bodies are JSON.</li>
 * </ul>
 */
class RequestBodyLimitFilter extends OncePerRequestFilter {

	private final int maxBytes;

	RequestBodyLimitFilter(int maxBytes) {
		this.maxBytes = maxBytes;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long declaredLength = request.getContentLengthLong();
		if (declaredLength > maxBytes) {
			reject(response);
			return;
		}
		if (declaredLength >= 0 || request.getHeader(HttpHeaders.TRANSFER_ENCODING) == null) {
			chain.doFilter(request, response);
			return;
		}
		byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
		if (body.length > maxBytes) {
			reject(response);
			return;
		}
		chain.doFilter(new BufferedBodyRequest(request, body), response);
	}

	private static void reject(HttpServletResponse response) throws IOException {
		ProblemResponses.write(response, HttpStatus.BAD_REQUEST, "request_too_large", "The request body is too large.");
	}

	/** Replays an already read, size-checked body from memory. */
	private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		BufferedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public int getContentLength() {
			return body.length;
		}

		@Override
		public long getContentLengthLong() {
			return body.length;
		}

		@Override
		public ServletInputStream getInputStream() {
			return new InMemoryServletInputStream(body);
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset()));
		}

		/** The declared charset, or UTF-8 when none is declared or it is unknown. */
		private Charset charset() {
			String encoding = getCharacterEncoding();
			try {
				return (encoding != null) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
			}
			catch (IllegalArgumentException ex) {
				return StandardCharsets.UTF_8;
			}
		}

	}

	private static final class InMemoryServletInputStream extends ServletInputStream {

		private final ByteArrayInputStream delegate;

		InMemoryServletInputStream(byte[] body) {
			this.delegate = new ByteArrayInputStream(body);
		}

		@Override
		public int read() {
			return delegate.read();
		}

		@Override
		public int read(byte[] buffer, int offset, int length) {
			return delegate.read(buffer, offset, length);
		}

		@Override
		public boolean isFinished() {
			return delegate.available() == 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener listener) {
			throw new UnsupportedOperationException("Async reads are not supported for buffered bodies");
		}

	}

}
