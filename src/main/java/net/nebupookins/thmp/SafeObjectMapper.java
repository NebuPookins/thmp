package net.nebupookins.thmp;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fj.data.Either;

public class SafeObjectMapper {
	private final ObjectMapper delegate;

	public SafeObjectMapper(ObjectMapper delegate) {
		this.delegate = delegate;
	}

	public Either<JsonProcessingException,String> writeValueAsString(Object value) {
		try {
			return Either.right(delegate.writeValueAsString(value));
		} catch (JsonProcessingException e) {
			return Either.left(e);
		}
	}

	public <T> Either<JsonProcessingException, T> readValue(String src, Class<T> valueType) {
		try {
			return Either.right(delegate.readValue(src, valueType));
		} catch (JsonProcessingException e) {
			return Either.left(e);
		} catch (IOException e) {
			throw new RuntimeException("This should never happen.", e);
		}
	}
}
