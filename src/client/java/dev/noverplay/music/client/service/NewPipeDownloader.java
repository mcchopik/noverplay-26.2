package dev.noverplay.music.client.service;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class NewPipeDownloader extends Downloader {
	private final HttpClient client;
	private final String userAgent;

	public NewPipeDownloader(String userAgent) {
		this.userAgent = userAgent;
		this.client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	}

	@Override
	public Response execute(Request request) throws IOException, ReCaptchaException {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create(request.url()))
			.timeout(Duration.ofSeconds(15));

		String method = request.httpMethod();
		byte[] data = request.dataToSend();
		if ("POST".equalsIgnoreCase(method)) {
			builder.POST(HttpRequest.BodyPublishers.ofByteArray(data == null ? new byte[0] : data));
		} else if ("HEAD".equalsIgnoreCase(method)) {
			builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
		} else if ("GET".equalsIgnoreCase(method) || method == null || method.isBlank()) {
			builder.GET();
		} else {
			builder.method(method, data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(data));
		}

		boolean hasUserAgent = false;
		Map<String, List<String>> headers = request.headers();
		if (headers != null) {
			for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
				String key = entry.getKey();
				if ("User-Agent".equalsIgnoreCase(key)) {
					hasUserAgent = true;
				}
				List<String> values = entry.getValue();
				if (values == null || values.isEmpty()) {
					builder.header(key, "");
					continue;
				}
				for (String value : values) {
					builder.header(key, value);
				}
			}
		}
		if (!hasUserAgent && userAgent != null && !userAgent.isBlank()) {
			builder.header("User-Agent", userAgent);
		}

		HttpResponse<String> response;
		try {
			response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Request interrupted", e);
		}

		String body = response.body();
		if (response.statusCode() == 429 && body != null && body.toLowerCase().contains("captcha")) {
			throw new ReCaptchaException("reCAPTCHA required", request.url());
		}

		return new Response(
			response.statusCode(),
			"",
			response.headers().map(),
			body == null ? "" : body,
			response.uri().toString()
		);
	}
}
