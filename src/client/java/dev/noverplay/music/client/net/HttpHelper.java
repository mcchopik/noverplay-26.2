package dev.noverplay.music.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

public final class HttpHelper {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private HttpHelper() {
	}

	public static String getText(String url, Map<String, String> params, Map<String, String> headers)
		throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(withParams(url, params)))
			.timeout(Duration.ofSeconds(12))
			.GET()
			.headers(flattenHeaders(headers))
			.build();
		return CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
	}

	public static JsonElement getJson(String url, Map<String, String> params, Map<String, String> headers)
		throws IOException, InterruptedException {
		String body = getText(url, params, headers);
		return JsonParser.parseString(body);
	}

	private static String withParams(String url, Map<String, String> params) {
		if (params == null || params.isEmpty()) {
			return url;
		}
		StringJoiner joiner = new StringJoiner("&");
		for (Map.Entry<String, String> entry : params.entrySet()) {
			String key = encode(entry.getKey());
			String value = encode(entry.getValue());
			joiner.add(key + "=" + value);
		}
		String separator = url.contains("?") ? "&" : "?";
		return url + separator + joiner;
	}

	private static String encode(String value) {
		if (value == null) {
			return "";
		}
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String[] flattenHeaders(Map<String, String> headers) {
		if (headers == null || headers.isEmpty()) {
			return new String[0];
		}
		String[] flattened = new String[headers.size() * 2];
		int index = 0;
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			flattened[index++] = entry.getKey();
			flattened[index++] = entry.getValue();
		}
		return flattened;
	}
}
