package com.example.securedhello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.securedhello.config.ApiProperties;

/**
 * IM8 pm-6 (issue 17): {@code docs/api/openapi.yaml} is the API contract an integrator reads, and it is
 * hand-written, so nothing but a test stops it drifting from the application. Every assertion here
 * compares the document against the running application rather than against a second copy of the same
 * list: the operations come from Spring's handler mapping, and the session cookie and CSRF header names
 * come from a real bootstrap response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentTest {

	/** Repo-relative, so a failure names the file the reader has to edit. */
	private static final String DOCUMENT = "docs/api/openapi.yaml";

	/**
	 * The two operations that need no credential at all: {@code GET /csrf} issues the session cookie and
	 * the CSRF token rather than requiring them, and {@code POST /client-events} is the one CSRF-exempt
	 * endpoint (ADR 0002), which the SPA calls without credentials.
	 */
	private static final Set<String> UNSECURED = Set.of("GET /api/csrf", "POST /api/client-events");

	private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch",
			"trace");

	@Autowired
	MockMvc mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Autowired
	ApiProperties api;

	@Test
	void theDocumentDescribesExactlyTheEndpointsTheApplicationExposes() {
		Set<String> exposed = exposedOperations();
		Set<String> documented = documentedOperations();

		assertThat(exposed).as("the application must expose endpoints for the document to describe").isNotEmpty();
		assertThat(missingFrom(documented, exposed))
			.as("endpoints the application exposes that %s does not describe", DOCUMENT)
			.isEmpty();
		assertThat(missingFrom(exposed, documented))
			.as("operations %s describes that the application does not expose", DOCUMENT)
			.isEmpty();
	}

	/**
	 * Criterion 1: an error a caller has to handle is useless to them without its {@code code}, so every
	 * operation carries at least one ProblemDetail response, and every failure response in the document
	 * is that shared schema rather than a bespoke body.
	 */
	@Test
	void everyOperationDocumentsAtLeastOneProblemDetailErrorResponseWithACode() {
		assertThat(mapAt(schema("ProblemDetail"), "properties")).containsKey("code");

		operations().forEach((operation, definition) -> {
			Map<String, Object> responses = mapAt(definition, "responses");
			Set<String> failures = new TreeSet<>(responses.keySet());
			failures.removeIf((status) -> status.charAt(0) != '4' && status.charAt(0) != '5');

			assertThat(failures).as("failure responses documented for %s", operation).isNotEmpty();
			failures.forEach((status) -> assertThat(problemSchemaRef(responses, status))
				.as("schema of the %s response of %s", status, operation)
				.isEqualTo("#/components/schemas/ProblemDetail"));
		});
	}

	/**
	 * Criterion 2. The names are taken from a live bootstrap response, so renaming the cookie in
	 * {@code SessionCookieConfig} or the header in the CSRF repository fails this rather than leaving the
	 * document quietly wrong.
	 */
	@Test
	void theDocumentDeclaresTheSessionCookieAndCsrfTokenTheApplicationActuallyUses() throws Exception {
		MvcResult bootstrap = this.mvc.perform(get(this.api.path("/csrf"))).andExpect(status().isOk()).andReturn();
		String cookieName = bootstrap.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.map((cookie) -> cookie.substring(0, cookie.indexOf('=')))
			.findFirst()
			.orElseThrow();
		String headerName = JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.headerName");

		assertThat(securityScheme("sessionCookie")).containsEntry("type", "apiKey")
			.containsEntry("in", "cookie")
			.containsEntry("name", cookieName);
		assertThat(securityScheme("csrfToken")).containsEntry("type", "apiKey")
			.containsEntry("in", "header")
			.containsEntry("name", headerName);
	}

	/**
	 * Criterion 2, per operation. CSRF protection covers every state-changing endpoint but one, so the
	 * document says so operation by operation; the single exemption is checked against the application
	 * rather than trusted, because it is the one place a reader could mistake the document for a mistake.
	 */
	@Test
	void everyStateChangingOperationRequiresTheCsrfTokenExceptTheExemptOne() throws Exception {
		this.mvc
			.perform(post(this.api.path("/client-events")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"PAGE_LOAD\",\"path\":\"/login\",\"durationMs\":12}"))
			.andExpect(status().isNoContent());

		operations().forEach((operation, definition) -> {
			Set<String> required = requiredSchemes(definition);
			if (UNSECURED.contains(operation)) {
				assertThat(required).as("security requirements of %s", operation).isEmpty();
				return;
			}
			assertThat(required).as("security requirements of %s", operation).contains("sessionCookie");
			if (!operation.startsWith("GET ")) {
				assertThat(required).as("security requirements of %s", operation).contains("csrfToken");
			}
		});
	}

	/**
	 * A dangling {@code $ref} is the mistake a hand-written document is most prone to, and the one a
	 * reader hits as a broken rendering rather than as a wrong statement. Every reference here is
	 * internal, so all of them must resolve within the document.
	 */
	@Test
	void everyInternalReferenceResolves() {
		Map<String, Object> document = document();
		Set<String> references = new TreeSet<>();
		collectReferences(document, references);

		assertThat(references).as("the document uses internal references").isNotEmpty();
		references.forEach((reference) -> assertThat(resolve(document, reference))
			.as("%s resolves within %s", reference, DOCUMENT)
			.isNotNull());
	}

	private static void collectReferences(Object node, Set<String> references) {
		if (node instanceof Map<?, ?> map) {
			map.forEach((key, value) -> {
				if ("$ref".equals(key)) {
					references.add(value.toString());
				}
				else {
					collectReferences(value, references);
				}
			});
		}
		else if (node instanceof List<?> list) {
			list.forEach((element) -> collectReferences(element, references));
		}
	}

	/** Resolves a local JSON pointer such as {@code #/components/schemas/ProblemDetail}. */
	private static Object resolve(Map<String, Object> document, String reference) {
		assertThat(reference).as("every reference is internal to the document").startsWith("#/");
		Object node = document;
		for (String segment : reference.substring(2).split("/")) {
			if (!(node instanceof Map<?, ?> map)) {
				return null;
			}
			node = map.get(segment.replace("~1", "/").replace("~0", "~"));
		}
		return node;
	}

	/** Every {@code /api} operation Spring will dispatch, as {@code "METHOD /path"}. */
	private Set<String> exposedOperations() {
		Set<String> operations = new TreeSet<>();
		for (RequestMappingInfo info : this.handlerMapping.getHandlerMethods().keySet()) {
			for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
				if (!pattern.startsWith(this.api.basePath() + "/")) {
					continue;
				}
				for (RequestMethod method : info.getMethodsCondition().getMethods()) {
					operations.add(method.name() + " " + pattern);
				}
			}
		}
		return operations;
	}

	/** Every operation the document declares, in the same {@code "METHOD /path"} shape. */
	private Set<String> documentedOperations() {
		return new TreeSet<>(operations().keySet());
	}

	private Map<String, Map<String, Object>> operations() {
		Map<String, Map<String, Object>> operations = new LinkedHashMap<>();
		mapAt(document(), "paths").forEach((path, item) -> asMap(item).forEach((key, definition) -> {
			if (HTTP_METHODS.contains(key.toLowerCase(Locale.ROOT))) {
				operations.put(key.toUpperCase(Locale.ROOT) + " " + path, asMap(definition));
			}
		}));
		return operations;
	}

	/** The scheme names a caller must satisfy, flattened across the operation's {@code security} list. */
	private static Set<String> requiredSchemes(Map<String, Object> operation) {
		Object security = operation.get("security");
		assertThat(security).as("every operation declares its own security, even when it is empty").isNotNull();
		Set<String> schemes = new TreeSet<>();
		for (Object requirement : (List<?>) security) {
			schemes.addAll(asMap(requirement).keySet());
		}
		return schemes;
	}

	private static String problemSchemaRef(Map<String, Object> responses, String status) {
		Map<String, Object> response = asMap(responses.get(status));
		Object reference = response.get("$ref");
		if (reference != null) {
			response = mapAt(mapAt(mapAt(document(), "components"), "responses"), localName(reference.toString()));
		}
		Map<String, Object> content = mapAt(response, "content");
		return mapAt(asMap(content.values().iterator().next()), "schema").get("$ref").toString();
	}

	private static Map<String, Object> securityScheme(String name) {
		return mapAt(mapAt(mapAt(document(), "components"), "securitySchemes"), name);
	}

	private static Map<String, Object> schema(String name) {
		return mapAt(mapAt(mapAt(document(), "components"), "schemas"), name);
	}

	private static String localName(String reference) {
		return reference.substring(reference.lastIndexOf('/') + 1);
	}

	private static Set<String> missingFrom(Set<String> present, Set<String> expected) {
		Set<String> missing = new TreeSet<>(expected);
		missing.removeAll(present);
		return missing;
	}

	private static Map<String, Object> mapAt(Map<String, Object> parent, String key) {
		Object value = parent.get(key);
		assertThat(value).as("%s declares %s", DOCUMENT, key).isNotNull();
		return asMap(value);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return (Map<String, Object>) value;
	}

	/**
	 * Loaded with the safe constructor, so the document cannot instantiate a class if it ever gains a
	 * tag; nothing here needs anything beyond plain maps, lists and scalars.
	 */
	private static Map<String, Object> document() {
		try (Reader reader = Files.newBufferedReader(documentPath())) {
			return asMap(new Yaml(new SafeConstructor(new LoaderOptions())).load(reader));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not read " + DOCUMENT, ex);
		}
	}

	/** Found by walking up, so the test passes whether Maven runs in the module or the repo root. */
	private static Path documentPath() {
		Path start = Path.of("").toAbsolutePath();
		for (Path directory = start; directory != null; directory = directory.getParent()) {
			Path candidate = directory.resolve(DOCUMENT);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException(DOCUMENT + " not found in or above " + start);
	}

}
