/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.support;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

/**
 * {@link MessageHeaderAccessor} sub-class that supports storage and access of
 * headers from an external source such as a message broker. Headers from the
 * external source are kept separate from other headers, in a sub-map under the
 * key {@link #NATIVE_HEADERS}. This allows separating processing headers from
 * headers that need to be sent to or received from the external source.
 *
 * <p>This class is likely to be used through indirectly through a protocol
 * specific sub-class that also provide factory methods to translate
 * message headers to an from an external messaging source.
 *
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class NativeMessageHeaderAccessor extends MessageHeaderAccessor {

	/** The header name used to store native headers. */
	public static final String NATIVE_HEADERS = "nativeHeaders";


	/**
	 * Protected constructor to create a new instance.
	 */
	protected NativeMessageHeaderAccessor() {
		this((Map<String, List<String>>) null);
	}

	/**
	 * Protected constructor to create an instance with the given native headers.
	 * @param nativeHeaders native headers to create the message with (may be {@code null})
	 */
	protected NativeMessageHeaderAccessor(@Nullable Map<String, List<String>> nativeHeaders) {
		if (!CollectionUtils.isEmpty(nativeHeaders)) {
			setHeader(NATIVE_HEADERS, new LinkedMultiValueMap<>(nativeHeaders));
		}
	}

	/**
	 * Protected constructor that copies headers from another message.
	 */
	protected NativeMessageHeaderAccessor(@Nullable Message<?> message) {
		super(message);
		if (message != null) {
			@SuppressWarnings("unchecked")
			Map<String, List<String>> map = (Map<String, List<String>>) getHeader(NATIVE_HEADERS);
			if (map != null) {
				setHeader(NATIVE_HEADERS, new LinkedMultiValueMap<>(map));
			}
		}
	}


	/**
	 * Sub-classes can use this method to access the "native" headers sub-map.
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	protected Map<String, List<String>> getNativeHeaders() {
		return (Map<String, List<String>>) getHeader(NATIVE_HEADERS);
	}

	/**
	 * Return a copy of the native headers sub-map, or an empty map.
	 */
	public Map<String, List<String>> toNativeHeaderMap() {
		Map<String, List<String>> map = getNativeHeaders();
		return (map != null ? new LinkedMultiValueMap<>(map) : Collections.emptyMap());
	}

	@Override
	public void setImmutable() {
		if (isMutable()) {
			Map<String, List<String>> map = getNativeHeaders();
			if (map != null) {
				setHeader(NATIVE_HEADERS, Collections.unmodifiableMap(map));
			}
			super.setImmutable();
		}
	}

	@Override
	public void setHeader(String name, @Nullable Object value) {
		if (name.equalsIgnoreCase(NATIVE_HEADERS)) {
			// Force removal since setHeader checks for equality
			super.setHeader(NATIVE_HEADERS, null);
		}
		super.setHeader(name, value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void copyHeaders(@Nullable Map<String, ?> headersToCopy) {
		if (headersToCopy != null) {
			Map<String, List<String>> nativeHeaders = getNativeHeaders();
			Map<String, List<String>> map = (Map<String, List<String>>) headersToCopy.get(NATIVE_HEADERS);
			if (map != null) {
				if (nativeHeaders != null) {
					nativeHeaders.putAll(map);
				}
				else {
					nativeHeaders = new LinkedMultiValueMap<>(map);
				}
			}
			super.copyHeaders(headersToCopy);
			setHeader(NATIVE_HEADERS, nativeHeaders);
		}
	}

	/**
	 * Whether the native header map contains the give header name.
	 */
	public boolean containsNativeHeader(String headerName) {
		Map<String, List<String>> map = getNativeHeaders();
		return (map != null && map.containsKey(headerName));
	}

	/**
	 * Return the values for the specified native header, if present.
	 */
	@Nullable
	public List<String> getNativeHeader(String headerName) {
		Map<String, List<String>> map = getNativeHeaders();
		return (map != null ? map.get(headerName) : null);
	}

	/**
	 * Return the first value for the specified native header, if present.
	 */
	@Nullable
	public String getFirstNativeHeader(String headerName) {
		Map<String, List<String>> map = getNativeHeaders();
		if (map != null) {
			List<String> values = map.get(headerName);
			if (values != null) {
				return values.get(0);
			}
		}
		return null;
	}

	/**
	 * Set the specified native header value replacing existing values.
	 * <p>In order for this to work, the accessor must be {@link #isMutable()
	 * mutable}. See {@link MessageHeaderAccessor} for details.
	 */
	public void setNativeHeader(String name, @Nullable String value) {
		Assert.state(isMutable(), "Already immutable");
		Map<String, List<String>> map = getNativeHeaders();
		if (value == null) {
			if (map != null && map.get(name) != null) {
				setModified(true);
				map.remove(name);
			}
			return;
		}
		if (map == null) {
			map = new LinkedMultiValueMap<>(4);
			setHeader(NATIVE_HEADERS, map);
		}
		List<String> values = new LinkedList<>();
		values.add(value);
		if (!ObjectUtils.nullSafeEquals(values, getHeader(name))) {
			setModified(true);
			map.put(name, values);
		}
	}

	/**
	 * Add the specified native header value to existing values.
	 * <p>In order for this to work, the accessor must be {@link #isMutable()
	 * mutable}. See {@link MessageHeaderAccessor} for details.
	 */
	public void addNativeHeader(String name, @Nullable String value) {
		Assert.state(isMutable(), "Already immutable");
		if (value == null) {
			return;
		}
		Map<String, List<String>> nativeHeaders = getNativeHeaders();
		if (nativeHeaders == null) {
			nativeHeaders = new LinkedMultiValueMap<>(4);
			setHeader(NATIVE_HEADERS, nativeHeaders);
		}
		List<String> values = nativeHeaders.computeIfAbsent(name, k -> new LinkedList<>());
		values.add(value);
		setModified(true);
	}

	public void addNativeHeaders(@Nullable MultiValueMap<String, String> headers) {
		if (headers == null) {
			return;
		}
		headers.forEach((key, values) -> values.forEach(value -> addNativeHeader(key, value)));
	}

	/**
	 * Remove the specified native header value replacing existing values.
	 * <p>In order for this to work, the accessor must be {@link #isMutable()
	 * mutable}. See {@link MessageHeaderAccessor} for details.
	 */
	@Nullable
	public List<String> removeNativeHeader(String name) {
		Assert.state(isMutable(), "Already immutable");
		Map<String, List<String>> nativeHeaders = getNativeHeaders();
		if (nativeHeaders == null) {
			return null;
		}
		return nativeHeaders.remove(name);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public static String getFirstNativeHeader(String headerName, Map<String, Object> headers) {
		Map<String, List<String>> map = (Map<String, List<String>>) headers.get(NATIVE_HEADERS);
		if (map != null) {
			List<String> values = map.get(headerName);
			if (values != null) {
				return values.get(0);
			}
		}
		return null;
	}

}