/**
 * Copyright (C) 2017 HttpBuilder-NG Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovyx.net.http;

import groovy.lang.GString;
import org.codehaus.groovy.runtime.GStringImpl;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static groovyx.net.http.Traverser.notValue;
import static groovyx.net.http.Traverser.traverse;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;

/**
 * Provides a simple means of creating a request URI and optionally overriding its parts.
 *
 * [source,groovy]
 * ----
 * def uri = UriBuilder.basic(UriBuilder.root())
 * .setFull('http://localhost:10101')
 * .setPath('/foo')
 * .toURI()
 * ----
 *
 * Generally, this class is not instantiated directly, but created by the {@link HttpConfig} instance and modified.
 */
public abstract class UriBuilder {

	public static final int DEFAULT_PORT = -1;

    /**
     * Sets the scheme part of the URI.
     *
     * @param val the value to use as the scheme part of the URI
     * @return a reference to this builder
     */
    public abstract UriBuilder setScheme(String val);

	/**
	 * Retrieves the scheme part of the URI.
	 *
	 * @return the URI scheme
	 */
	public abstract String getScheme();

    /**
     * Sets the port part of the URI.
     *
     * @param val the value to use as the port part of the URI
     * @return a reference to this builder
     */
    public abstract UriBuilder setPort(int val);

	/**
	 * Retrieves the port part of the URI.
	 *
	 * @return the port part of the URI
	 */
	public abstract int getPort();

    /**
     * Sets the host part of the URI.
     *
     * @param val the value to use as the host part of the URI
     * @return a reference to this builder
     */
    public abstract UriBuilder setHost(String val);

	/**
	 * Retrieves the host part of the URI.
	 *
	 * @return the host part of the URI
	 */
	public abstract String getHost();

    /**
     * Sets the path part of the URI.
     *
     * @param val the path part of the URI
     * @return a reference to the builder
     */
    public abstract UriBuilder setPath(GString val);

	/**
	 * Retrieves the path part of the URI.
	 *
	 * @return the path part of the URI
	 */
	public abstract GString getPath();

    /**
     * Sets the query string part of the `URI` from the provided map. The query string key-value pairs will be generated from the key-value pairs
     * of the map and are NOT URL-encoded. Nested maps or other data structures are not supported.
     *
     * @param val the map of query string parameters
     * @return a reference to the builder
     */
    public abstract UriBuilder setQuery(Map<String, ?> val);

	/**
	 * Retrieves the `Map` of query string parameters for the `URI`.
	 *
	 * @return the `Map` of query string parameters for the `URI`.
	 */
	public abstract Map<String, ?> getQuery();

    /**
     * Sets the fragment part of the `URI`.
     *
     * @param val the fragment part of the `URI`
     * @return a reference to the builder
     */
    public abstract UriBuilder setFragment(String val);

	/**
	 * Retrieves the fragment part of the `URI`.
	 *
	 * @return the fragment part of the `URI`
	 */
	public abstract String getFragment();

    /**
     * Sets the user info part of the `URI`.
     *
     * @param val the user info part of the `URI`
     * @return a reference to the builder
     */
    public abstract UriBuilder setUserInfo(String val);

	/**
	 * Retrieves the user info part of the `URI`.
	 *
	 * @return the user info part of the `URI`
	 */
	public abstract String getUserInfo();

	public abstract UriBuilder getParent();

    /**
     * Sets the path part of the URI.
     *
     * @param chars a {@link CharSequence} ({@link String} or {@link GString})
     *
     * @return a reference to the builder
     */
    public UriBuilder setPath(final CharSequence chars) {
		final String[] parts;
        if (chars.charAt(0) == '/') {
			parts = new String[] { chars.toString() };
		} else {
			final String base = getPath().toString();
			parts = new String[] { base, base.endsWith("/") ? "" : "/", chars.toString() };
		}
		return setPath(new GStringImpl(EMPTY, parts));
	}

	public URI forCookie(final HttpCookie cookie) throws URISyntaxException {
		final String scheme = traverse(this, UriBuilder::getParent, UriBuilder::getScheme, Traverser::notNull);
		final Integer port = traverse(this, UriBuilder::getParent, UriBuilder::getPort, notValue(DEFAULT_PORT));
		final String host = traverse(this, UriBuilder::getParent, UriBuilder::getHost, Traverser::notNull);
		final String path = cookie.getPath();
		final String query = null;
		final String fragment = null;
		final String userInfo = null;

		return new URI(scheme, userInfo, host, (port == null ? -1 : port), path, query, fragment);
	}

	/**
	 * Converts the parts of the `UriBuilder` to the `URI` object instance.
	 *
	 * @return the generated `URI` representing the parts contained in the builder
	 */
	public URI toURI() throws URISyntaxException {
		final String scheme = traverse(this, UriBuilder::getParent, UriBuilder::getScheme, Traverser::notNull);
		final Integer port = traverse(this, UriBuilder::getParent, UriBuilder::getPort, notValue(DEFAULT_PORT));
		final String host = traverse(this, UriBuilder::getParent, UriBuilder::getHost, Traverser::notNull);
		final GString path = traverse(this, UriBuilder::getParent, UriBuilder::getPath, Traverser::notNull);
		final String query = populateQueryString(traverse(this, UriBuilder::getParent, UriBuilder::getQuery, Traverser::nonEmptyMap));
		final String fragment = traverse(this, UriBuilder::getParent, UriBuilder::getFragment, Traverser::notNull);
		final String userInfo = traverse(this, UriBuilder::getParent, UriBuilder::getUserInfo, Traverser::notNull);
		final Boolean useRaw = traverse(this, UriBuilder::getParent, UriBuilder::getUseRawValues, Traverser::notNull);

		if (useRaw != null && useRaw) {
			return toRawURI(scheme, port, host, path, query, fragment, userInfo);
		} else {
			return new URI(scheme, userInfo, host, (port == null ? -1 : port), ((path == null) ? null : path.toString()), query, fragment);
		}
	}

    private URI toRawURI(final String scheme, final Integer port, final String host, final GString path, final String query, final String fragment, final String userInfo) throws URISyntaxException {
        return new URI(format("%s%s%s%s%s%s%s",
            scheme == null ? "" : (scheme.endsWith("://") ? scheme : scheme + "://"),
            userInfo == null ? "" : (userInfo.endsWith("@") ? userInfo : userInfo + "@"),
            host == null ? "" : host,
            port == null ? "" : ":" + port.toString(),
            path == null ? "" : (!path.toString().startsWith("/") && !path.toString().isEmpty() ? "/" + path : path),
            query != null ? "?" + query : "",
            fragment == null ? "" : (!fragment.startsWith("#") ? "#" + fragment : fragment)
        ));
    }

	private static final Object[] EMPTY = new Object[0];

	private static String populateQueryString(final Map<String, ?> queryMap) {
		if (queryMap == null || queryMap.isEmpty()) {
			return null;

		} else {
			final List<String> nvps = new LinkedList<>();

            queryMap.entrySet().forEach((Consumer<Map.Entry<String, ?>>) entry -> {
                final Collection<?> values = entry.getValue() instanceof Collection ? (Collection<?>) entry.getValue() : singletonList(entry.getValue().toString());
                if(values.isEmpty()){
                    nvps.add(entry.getKey());
                } else {
                    values.forEach(value -> nvps.add(entry.getKey() + "=" + value));
                }
            });

			return nvps.stream().collect(Collectors.joining("&"));
		}
	}

	private Boolean useRawValues;

	public void setUseRawValues(final boolean useRaw) {
		this.useRawValues = useRaw;
	}

	public Boolean getUseRawValues() {
		return useRawValues;
	}

	protected final void populateFrom(final URI uri) {
		boolean useRaw = useRawValues != null ? useRawValues : false;

		try {
			setScheme(uri.getScheme());
			setPort(uri.getPort());
			setHost(uri.getHost());

            final String path = useRaw ? uri.getRawPath() : uri.getPath();
            if (path != null) {
                setPath(new GStringImpl(EMPTY, new String[]{path}));
            }

			final String rawQuery = useRaw ? uri.getRawQuery() : uri.getQuery();
			if (rawQuery != null) {
				if (useRaw) {
					setQuery(extractQueryMap(rawQuery));
				} else {
					setQuery(Form.decode(new StringBuilder(rawQuery), UTF_8));
				}
			}

			setFragment(useRaw ? uri.getRawFragment() : uri.getFragment());
			setUserInfo(useRaw ? uri.getRawUserInfo() : uri.getUserInfo());
		} catch (IOException e) {
			//this seems o.k. to just convert to a runtime exception,
			//we started with a valid URI, so this should never happen.
			throw new RuntimeException(e);
		}
	}

	// does not do any encoding
	private static Map<String, Collection<String>> extractQueryMap(final String queryString) {
		final Map<String, Collection<String>> map = new HashMap<>();

		for (final String nvp : queryString.split("&")) {
			final String[] pair = nvp.split("=");
			map.computeIfAbsent(pair[0], k -> {
				List<String> list = new LinkedList<>();
				list.add(pair[1]);
				return list;
			});
		}

		return map;
	}

    /**
     * Sets the full URI (all parts) as a String.
     *
     * @param str the full URI to be used by the `UriBuilder`
     * @return a reference to the builder
     * @throws IllegalArgumentException if there is a problem with the URI syntax
     */
    public final UriBuilder setFull(final String str) {
        try {
            return setFull(new URI(str));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
    }

    /**
     * Sets the full URI (all parts) as a URI object.
     *
     * @param uri the full URI to be used by the `UriBuilder`
     * @return a reference to the builder
     */
    public final UriBuilder setFull(final URI uri) {
        populateFrom(uri);
        return this;
    }

    /**
     * Creates a basic `UriBuilder` from the provided parent builder. An empty `UriBuilder` may be created using the `root()` method as the `parent` value,
     * otherwise a new `UriBuilder` may be created from an existing builder:
     *
     * [source,groovy]
     * ----
     * def parent = UriBuilder.basic(UriBuilder.root()).setFull('http://localhost:10101/foo')
     * def child = UriBuilder.basic(parent)
     * child.setPath('/bar').toURI() == new URI('http://localhost:10101/bar')
     * ----
     *
     * The `UriBuilder` implementation generated with this method is _not_ thread-safe.
     *
     * @param parent the `UriBuilder` parent
     * @return the created `UriBuilder`
     */
    public static UriBuilder basic(final UriBuilder parent) {
        return new Basic(parent);
    }

    /**
     * Creates a thread-safe `UriBuilder` from the provided parent builder. An empty `UriBuilder` may be created using the `root()` method as the
     * `parent` value, otherwise a new `UriBuilder` may be created from an existing builder:
     *
     * [source,groovy]
     * ----
     * def parent = UriBuilder.threadSafe(UriBuilder.root()).setFull('http://localhost:10101/foo')
     * def child = UriBuilder.threadSafe(parent)
     * child.setPath('/bar').toURI() == new URI('http://localhost:10101/bar')
     * ----
     *
     * The `UriBuilder` implementation generated with this method is thread-safe.
     *
     * @param parent the `UriBuilder` parent
     * @return the created `UriBuilder`
     */
    public static UriBuilder threadSafe(final UriBuilder parent) {
        return new ThreadSafe(parent);
    }

	public static UriBuilder root() {
		return new ThreadSafe(null);
	}

    private static final class Basic extends UriBuilder {
        private String scheme;

		public UriBuilder setScheme(String val) {
			scheme = val;
			return this;
		}

		public String getScheme() {
			return scheme;
		}

		private int port = DEFAULT_PORT;

		public UriBuilder setPort(int val) {
			port = val;
			return this;
		}

		public int getPort() {
			return port;
		}

		private String host;

		public UriBuilder setHost(String val) {
			host = val;
			return this;
		}

		public String getHost() {
			return host;
		}

		private GString path;

		public UriBuilder setPath(GString val) {
			path = val;
			return this;
		}

		public GString getPath() {
			return path;
		}

		private Map<String, Object> query = new LinkedHashMap<>(1);

		public UriBuilder setQuery(final Map<String, ?> val) {
			if (val != null) {
				query.putAll(val);
			}
			return this;
		}

		public Map<String, Object> getQuery() {
			return query;
		}

		private String fragment;

		public UriBuilder setFragment(String val) {
			fragment = val;
			return this;
		}

		public String getFragment() {
			return fragment;
		}

		private String userInfo;

		public UriBuilder setUserInfo(String val) {
			userInfo = val;
			return this;
		}

		public String getUserInfo() {
			return userInfo;
		}

		private final UriBuilder parent;

		public UriBuilder getParent() {
			return parent;
		}

		public Basic(final UriBuilder parent) {
			this.parent = parent;
		}
	}

    private static final class ThreadSafe extends UriBuilder {
        private volatile String scheme;

		public UriBuilder setScheme(String val) {
			scheme = val;
			return this;
		}

		public String getScheme() {
			return scheme;
		}

		private volatile int port = DEFAULT_PORT;

		public UriBuilder setPort(int val) {
			port = val;
			return this;
		}

		public int getPort() {
			return port;
		}

		private volatile String host;

		public UriBuilder setHost(String val) {
			host = val;
			return this;
		}

		public String getHost() {
			return host;
		}

		private volatile GString path;

		public UriBuilder setPath(GString val) {
			path = val;
			return this;
		}

		public GString getPath() {
			return path;
		}

		private Map<String, Object> query = new ConcurrentHashMap<>();

		public UriBuilder setQuery(Map<String, ?> val) {
			query.putAll(val);
			return this;
		}

		public Map<String, ?> getQuery() {
			return query;
		}

		private volatile String fragment;

		public UriBuilder setFragment(String val) {
			fragment = val;
			return this;
		}

		public String getFragment() {
			return fragment;
		}

		private volatile String userInfo;

		public UriBuilder setUserInfo(String val) {
			userInfo = val;
			return this;
		}

		public String getUserInfo() {
			return userInfo;
		}

		private final UriBuilder parent;

		public UriBuilder getParent() {
			return parent;
		}

		public ThreadSafe(final UriBuilder parent) {
			this.parent = parent;
		}
	}
}
