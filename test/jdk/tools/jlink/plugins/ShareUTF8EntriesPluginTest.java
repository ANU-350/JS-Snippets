/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test compressor
 * @author Ian Graves
 * @modules java.base/jdk.internal.jimage.decompressor
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jlink.plugin
 * @run main ShareUTF8EntriesPluginTest
 */

import jdk.internal.jimage.decompressor.*;
import jdk.tools.jlink.internal.ResourcePoolManager;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.internal.plugins.ShareUTF8EntriesPlugin;
import jdk.tools.jlink.internal.plugins.DefaultCompressPlugin;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShareUTF8EntriesPluginTest {
    private static int strID = 1;

    public static void main(String[] args) throws Exception {
        new ShareUTF8EntriesPluginTest().test();
    }

    public void test() throws Exception {
        FileSystem fs;
        try {
            fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
            System.err.println("Not an image build, test skipped.");
            return;
        }
        Path javabase = fs.getPath("/modules/java.base");
        ResourcePool classes = gatherClasses(javabase);
        ShareUTF8EntriesPlugin compressPlugin;

        // Compact Constant Pools
        Properties options1 = new Properties();
        compressPlugin = new ShareUTF8EntriesPlugin();
        checkCompress(classes, compressPlugin,
                options1,
                new ResourceDecompressorFactory[]{
                        new StringSharingDecompressorFactory()
                });

        // Compact Constant Pools + filter
        options1.setProperty(DefaultCompressPlugin.FILTER,
                "**Exception.class");
        compressPlugin = new ShareUTF8EntriesPlugin();
        checkCompress(classes, compressPlugin,
                options1,
                new ResourceDecompressorFactory[]{
                        new StringSharingDecompressorFactory()
                }, Collections.singletonList(".*Exception.class"));
    }

    private ResourcePool gatherResources(Path module) throws Exception {
        ResourcePoolManager poolMgr = new ResourcePoolManager(ByteOrder.nativeOrder(), new StringTable() {

            @Override
            public int addString(String str) {
                return -1;
            }

            @Override
            public String getString(int id) {
                return null;
            }
        });

        ResourcePoolBuilder poolBuilder = poolMgr.resourcePoolBuilder();
        try (Stream<Path> stream = Files.walk(module)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext();) {
                Path p = iterator.next();
                if (Files.isRegularFile(p)) {
                    byte[] content = Files.readAllBytes(p);
                    poolBuilder.add(ResourcePoolEntry.create(p.toString(), content));
                }
            }
        }
        return poolBuilder.build();
    }

    private ResourcePool gatherClasses(Path module) throws Exception {
        ResourcePoolManager poolMgr = new ResourcePoolManager(ByteOrder.nativeOrder(), new StringTable() {

            @Override
            public int addString(String str) {
                return -1;
            }

            @Override
            public String getString(int id) {
                return null;
            }
        });

        ResourcePoolBuilder poolBuilder = poolMgr.resourcePoolBuilder();
        try (Stream<Path> stream = Files.walk(module)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext();) {
                Path p = iterator.next();
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
                    byte[] content = Files.readAllBytes(p);
                    poolBuilder.add(ResourcePoolEntry.create(p.toString(), content));
                }
            }
        }
        return poolBuilder.build();
    }

    private void checkCompress(ResourcePool resources, Plugin prov,
                               Properties config,
                               ResourceDecompressorFactory[] factories) throws Exception {
        checkCompress(resources, prov, config, factories, Collections.emptyList());
    }

    private void checkCompress(ResourcePool resources, Plugin prov,
                               Properties config,
                               ResourceDecompressorFactory[] factories,
                               List<String> includes) throws Exception {
        if (factories.length == 0) {
            // no compression, nothing to check!
            return;
        }

        long[] original = new long[1];
        long[] compressed = new long[1];
        resources.entries().forEach(resource -> {
            List<Pattern> includesPatterns = includes.stream()
                    .map(Pattern::compile)
                    .collect(Collectors.toList());

            Map<String, String> props = new HashMap<>();
            if (config != null) {
                for (String p : config.stringPropertyNames()) {
                    props.put(p, config.getProperty(p));
                }
            }
            prov.configure(props);
            final Map<Integer, String> strings = new HashMap<>();
            ResourcePoolManager inputResourcesMgr = new ResourcePoolManager(ByteOrder.nativeOrder(), new StringTable() {
                @Override
                public int addString(String str) {
                    int id = strID;
                    strID += 1;
                    strings.put(id, str);
                    return id;
                }

                @Override
                public String getString(int id) {
                    return strings.get(id);
                }
            });
            inputResourcesMgr.add(resource);
            ResourcePool compressedResources = applyCompressor(prov, inputResourcesMgr, resource, includesPatterns);
            original[0] += resource.contentLength();
            compressed[0] += compressedResources.findEntry(resource.path()).get().contentLength();
            applyDecompressors(factories, inputResourcesMgr.resourcePool(), compressedResources, strings, includesPatterns);
        });
        String compressors = Stream.of(factories)
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        String size = "Compressed size: " + compressed[0] + ", original size: " + original[0];
        System.out.println("Used " + compressors + ". " + size);
        if (original[0] <= compressed[0]) {
            throw new AssertionError("java.base not compressed.");
        }
    }

    private ResourcePool applyCompressor(Plugin plugin,
                                         ResourcePoolManager inputResources,
                                         ResourcePoolEntry res,
                                         List<Pattern> includesPatterns) {
        ResourcePoolManager resMgr = new ResourcePoolManager(ByteOrder.nativeOrder(),
                inputResources.getStringTable());
        ResourcePool compressedResourcePool = plugin.transform(inputResources.resourcePool(),
                resMgr.resourcePoolBuilder());
        String path = res.path();
        ResourcePoolEntry compressed = compressedResourcePool.findEntry(path).get();
        CompressedResourceHeader header
                = CompressedResourceHeader.readFromResource(ByteOrder.nativeOrder(), compressed.contentBytes());
        if (isIncluded(includesPatterns, path)) {
            if (header == null) {
                throw new AssertionError("Path should be compressed: " + path);
            }
            if (header.getDecompressorNameOffset() == 0) {
                throw new AssertionError("Invalid plugin offset "
                        + header.getDecompressorNameOffset());
            }
            if (header.getResourceSize() <= 0) {
                throw new AssertionError("Invalid compressed size "
                        + header.getResourceSize());
            }
        } else if (header != null) {
            throw new AssertionError("Path should not be compressed: " + path);
        }
        return compressedResourcePool;
    }

    private void applyDecompressors(ResourceDecompressorFactory[] decompressors,
                                    ResourcePool inputResources,
                                    ResourcePool compressedResources,
                                    Map<Integer, String> strings,
                                    List<Pattern> includesPatterns) {
        compressedResources.entries().forEach(compressed -> {
            CompressedResourceHeader header = CompressedResourceHeader.readFromResource(
                    ByteOrder.nativeOrder(), compressed.contentBytes());
            String path = compressed.path();
            ResourcePoolEntry orig = inputResources.findEntry(path).get();
            if (!isIncluded(includesPatterns, path)) {
                return;
            }
            byte[] decompressed = compressed.contentBytes();
            for (ResourceDecompressorFactory factory : decompressors) {
                try {
                    ResourceDecompressor decompressor = factory.newDecompressor(new Properties());
                    decompressed = decompressor.decompress(
                            strings::get, decompressed,
                            CompressedResourceHeader.getSize(), header.getUncompressedSize());
                } catch (Exception exp) {
                    throw new RuntimeException(exp);
                }
            }

            if (decompressed.length != orig.contentLength()) {
                throw new AssertionError("Invalid uncompressed size "
                        + header.getUncompressedSize());
            }
            byte[] origContent = orig.contentBytes();
            for (int i = 0; i < decompressed.length; i++) {
                if (decompressed[i] != origContent[i]) {
                    throw new AssertionError("Decompressed and original differ at index " + i);
                }
            }
        });
    }

    private boolean isIncluded(List<Pattern> includesPatterns, String path) {
        return includesPatterns.isEmpty() ||
                includesPatterns.stream().anyMatch((pattern) -> pattern.matcher(path).matches());
    }
}
