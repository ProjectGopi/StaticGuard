package com.staticanalysis.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight multipart/form-data parser.
 * No external dependencies — uses only standard JDK classes.
 */
public class MultipartParser {

    public static class UploadedFile {
        public final String filename;
        public final byte[] content;

        public UploadedFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }
    }

    /**
     * Parse uploaded files from a multipart/form-data request body.
     *
     * @param contentType  The value of the Content-Type header (must contain "boundary=...")
     * @param bodyStream   The raw request body stream
     * @return             List of uploaded files extracted from the body
     */
    public static List<UploadedFile> parse(String contentType, InputStream bodyStream) throws IOException {
        List<UploadedFile> files = new ArrayList<>();

        // Extract boundary from Content-Type header
        String boundary = extractBoundary(contentType);
        if (boundary == null) return files;

        byte[] bodyBytes = readAllBytes(bodyStream);
        String bodyStr = new String(bodyBytes, StandardCharsets.ISO_8859_1);

        String delimiter = "--" + boundary;

        int pos = 0;
        while (pos < bodyStr.length()) {
            int delimPos = bodyStr.indexOf(delimiter, pos);
            if (delimPos < 0) break;

            int afterDelim = delimPos + delimiter.length();

            // Check for end boundary
            if (afterDelim + 2 <= bodyStr.length() &&
                bodyStr.substring(afterDelim, Math.min(afterDelim + 2, bodyStr.length())).equals("--")) {
                break;
            }

            // Skip CRLF after boundary
            int headerStart = afterDelim;
            if (headerStart < bodyStr.length() && bodyStr.charAt(headerStart) == '\r') headerStart++;
            if (headerStart < bodyStr.length() && bodyStr.charAt(headerStart) == '\n') headerStart++;

            // Find end of headers (double CRLF)
            int headerEnd = bodyStr.indexOf("\r\n\r\n", headerStart);
            if (headerEnd < 0) { pos = afterDelim + 1; continue; }

            String headers = bodyStr.substring(headerStart, headerEnd);

            // Find filename in Content-Disposition header
            String filename = extractFilename(headers);

            // Content starts after the double CRLF
            int contentStart = headerEnd + 4;

            // Content ends just before the next boundary
            int nextBoundary = bodyStr.indexOf("\r\n" + delimiter, contentStart);
            if (nextBoundary < 0) { pos = afterDelim + 1; continue; }

            byte[] fileContent = new byte[nextBoundary - contentStart];
            System.arraycopy(bodyBytes, contentStart, fileContent, 0, fileContent.length);

            if (filename != null && !filename.isEmpty()) {
                files.add(new UploadedFile(filename, fileContent));
            }

            pos = nextBoundary + 2; // move past \r\n
        }

        return files;
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length()).trim().replace("\"", "");
            }
        }
        return null;
    }

    private static String extractFilename(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-disposition")) {
                for (String part : line.split(";")) {
                    part = part.trim();
                    if (part.startsWith("filename=")) {
                        return part.substring("filename=".length()).replace("\"", "").trim();
                    }
                }
            }
        }
        return null;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}
