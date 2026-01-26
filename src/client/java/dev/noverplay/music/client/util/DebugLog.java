package dev.noverplay.music.client.util;

import dev.noverplay.music.NoverPlay;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DebugLog {
	private static final DateTimeFormatter TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
	private static final AtomicBoolean INIT = new AtomicBoolean(false);
	private static final int DEFAULT_MAX_BODY = 20000;
	private static final int DEFAULT_PREVIEW = 64;

	private static Path logPath;
	private static int maxBodyChars;
	private static int previewChars;

	private DebugLog() {
	}

	public static void init() {
		if (!INIT.compareAndSet(false, true)) {
			return;
		}
		logPath = resolveLogPath();
		maxBodyChars = Integer.getInteger("noverplay.log.maxBody", DEFAULT_MAX_BODY);
		previewChars = Integer.getInteger("noverplay.log.preview", DEFAULT_PREVIEW);
		write("INFO", "Debug log started: " + logPath, null);
	}

	public static Path getLogPath() {
		init();
		return logPath;
	}

	public static void info(String message) {
		write("INFO", message, null);
	}

	public static void warn(String message) {
		write("WARN", message, null);
	}

	public static void error(String message, Throwable error) {
		write("ERROR", message, error);
	}

	public static void httpRequest(String method, String url, Map<String, String> headers) {
		String headerText = headers == null || headers.isEmpty() ? "{}" : headers.toString();
		info("HTTP " + method + " " + url + " headers=" + headerText);
	}

	public static void httpResponse(String url, int status, Map<String, List<String>> headers, String body) {
		String headerText = headers == null || headers.isEmpty() ? "{}" : headers.toString();
		info("HTTP response " + status + " " + url + " headers=" + headerText);
		if (body == null) {
			info("HTTP body: <null>");
			return;
		}
		String payload = maybeTrim(body, maxBodyChars);
		info("HTTP body (" + body.length() + " chars): " + payload);
	}

	public static String preview(String value) {
		return maybeTrim(value, previewChars);
	}

	private static String maybeTrim(String value, int limit) {
		if (value == null) {
			return "";
		}
		if (limit <= 0 || value.length() <= limit) {
			return value;
		}
		return value.substring(0, limit) + "...(truncated " + (value.length() - limit) + " chars)";
	}

	private static void write(String level, String message, Throwable error) {
		init();
		String timestamp = LocalDateTime.now().format(TIMESTAMP);
		String thread = Thread.currentThread().getName();
		StringBuilder builder = new StringBuilder(256);
		builder.append(timestamp)
			.append(" [").append(level).append("]")
			.append(" [").append(thread).append("] ")
			.append(message == null ? "" : message);
		if (error != null) {
			builder.append(System.lineSeparator());
			builder.append(stackTrace(error));
		}
		String line = builder.toString() + System.lineSeparator();
		synchronized (DebugLog.class) {
			try {
				Files.writeString(
					logPath,
					line,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND
				);
			} catch (IOException e) {
				NoverPlay.LOGGER.warn("Failed to write debug log", e);
			}
		}
	}

	private static String stackTrace(Throwable error) {
		StringWriter writer = new StringWriter();
		PrintWriter printer = new PrintWriter(writer);
		error.printStackTrace(printer);
		printer.flush();
		return writer.toString();
	}

	private static Path resolveLogPath() {
		String userDir = System.getProperty("user.dir", ".");
		Path base = Paths.get(userDir);
		Path fileName = base.getFileName();
		if (fileName != null && "run".equalsIgnoreCase(fileName.toString())) {
			Path parent = base.getParent();
			if (parent != null) {
				base = parent;
			}
		}
		return base.resolve("noverplay-debug.log");
	}
}
