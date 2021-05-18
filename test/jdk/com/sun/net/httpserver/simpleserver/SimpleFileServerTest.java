/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Basic tests for SimpleFileServerTest
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.URIBuilder
 * @run testng/othervm SimpleFileServerTest
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.Platform;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.*;

public class SimpleFileServerTest {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @BeforeTest
    public void setup() {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
    }

    @Test
    public void testFileGET() throws Exception {
        var root = Files.createDirectory(CWD.resolve("testFileGET"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "some text");
            assertEquals(response.headers().firstValue("content-type").get(), "text/plain");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testDirectoryGET() throws Exception {
        // TODO: why listing for >>>>&#x2F<<<<;
        var expectedBody = """
                <!DOCTYPE html>
                <html>
                <body>
                <h2>Directory listing for &#x2F;</h2>
                <ul>
                <li><a href="yFile.txt">yFile.txt</a></li>
                </ul><p><hr>
                </body>
                </html>
                """;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = Files.createDirectory(CWD.resolve("testDirectoryGET"));
        var file = Files.writeString(root.resolve("yFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(root);

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), expectedBody);
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testFileHEAD() throws Exception {
        var root = Files.createDirectory(CWD.resolve("testFileHEAD"));
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "aFile.txt"))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/plain");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), "");
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testDirectoryHEAD() throws Exception {
        var expectedLength = Integer.toString(
                """
                <!DOCTYPE html>
                <html>
                <body>
                <h2>Directory listing for &#x2F;</h2>
                <ul>
                <li><a href="yFile.txt">yFile.txt</a></li>
                </ul><p><hr>
                </body>
                </html>
                """.getBytes(UTF_8).length);
        var root = Files.createDirectory(CWD.resolve("testDirectoryHEAD"));
        var file = Files.writeString(root.resolve("yFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(root);

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, ""))
                    .method("HEAD", BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), "");
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testMovedPermanently() throws Exception {
        var root = Files.createDirectory(CWD.resolve("testMovedPermanently"));
        Files.createDirectory(root.resolve("aDirectory"));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var uri = uri(ss, "aDirectory");
            var request = HttpRequest.newBuilder(uri).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 301);
            assertEquals(response.headers().firstValue("content-length").get(), "0");
            assertEquals(response.headers().firstValue("location").get(), "%s/".formatted(uri));
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testForbidden() throws Exception {
        if (!Platform.isWindows()) {  // not applicable on Windows
            var root = Files.createDirectory(CWD.resolve("testForbidden"));
            var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);

            // make file not readable
            file.toFile().setReadable(false, false);
            assert !Files.isReadable(file);

            var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
            ss.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
                var request = HttpRequest.newBuilder(uri(ss, "aFile.txt")).build();
                var response = client.send(request, BodyHandlers.ofString());
                assertEquals(response.statusCode(), 403);
                assertEquals(response.headers().firstValue("content-length").get(), "0");
            } finally {
                ss.stop(0);
                file.toFile().setReadable(true, false);
            }
        }
    }

    @Test
    public void testNotFound() throws Exception {
        var root = Files.createDirectory(CWD.resolve("testNotFound"));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "doesNotExist.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), "48");
            assertTrue(response.body().contains("not found"));  // TODO: why partial html reply?
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testNull() {
        final var addr = InetSocketAddress.createUnresolved("foo", 8080);
        final var path = Path.of("/tmp");
        final var levl = OutputLevel.INFO;
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, null, null));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, null, levl));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, path, null));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(null, path, levl));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(addr, null, null));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(addr, null, levl));
        assertThrows(NPE, () -> SimpleFileServer.createFileServer(addr, path, null));

        assertThrows(NPE, () -> SimpleFileServer.createFileHandler(null));

        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(null, null));
        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(null, levl));
        assertThrows(NPE, () -> SimpleFileServer.createOutputFilter(System.out, null));
    }

    @Test
    public void testInitialSlashContext() {
        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, CWD, OutputLevel.INFO);
        ss.removeContext("/"); // throws if no context.
        ss.stop(0);
    }

    @Test
    public void testBound() {
        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, CWD, OutputLevel.INFO);
        var boundAddr = ss.getAddress();
        ss.stop(0);
        assertTrue(boundAddr.getAddress() != null);
        assertTrue(boundAddr.getPort() > 0);
    }

    @Test
    public void testIllegalPath() throws IOException {
        var addr = LOOPBACK_ADDR;
        {   // not absolute
            Path p = Path.of(".");
            assert Files.isDirectory(p);
            assert Files.exists(p);
            assert !p.isAbsolute();
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
            assertTrue(iae.getMessage().contains("is not absolute"));
        }
        {   // not a directory
            Path p = Files.createFile(CWD.resolve("aFile"));
            assert !Files.isDirectory(p);
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
            assertTrue(iae.getMessage().contains("not a directory"));
        }
        {   // does not exist
            Path p = CWD.resolve("doesNotExist");
            assert !Files.exists(p);
            var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
            assertTrue(iae.getMessage().contains("does not exist"));
        }
        {   // not readable
            if (!Platform.isWindows()) {  // not reliable on Windows
                Path p = Files.createDirectory(CWD.resolve("aDir"));
                p.toFile().setReadable(false, false);
                assert !Files.isReadable(p);
                try {
                    var iae = expectThrows(IAE, () -> SimpleFileServer.createFileServer(addr, p, OutputLevel.INFO));
                    assertTrue(iae.getMessage().contains("not readable"));
                } finally {
                    p.toFile().setReadable(true, false);
                }
            }
        }
    }

    @Test
    public void testXss() throws Exception {
        var root = Files.createDirectory(CWD.resolve("testXss"));

        var ss = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(ss, "beginDelim%3C%3EEndDelim")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertTrue(response.body().contains("beginDelim%3C%3EEndDelim"));
            assertTrue(response.body().contains("File not found"));
        } finally {
            ss.stop(0);
        }
    }

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss v");

    static String getLastModified(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.of("GMT"))
                .format(HTTP_DATE_FORMATTER);
    }
}
