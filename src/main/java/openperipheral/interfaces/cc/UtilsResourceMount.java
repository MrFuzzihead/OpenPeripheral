package openperipheral.interfaces.cc;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.ByteStreams;

import dan200.computercraft.api.filesystem.IMount;

public class UtilsResourceMount implements IMount {

    private static final String RESOURCE_PATH = "/openperipheral/lua/";
    private final SortedSet<String> files;
    private final ConcurrentMap<String, Long> sizes = new ConcurrentHashMap<String, Long>();

    public UtilsResourceMount() {
        ImmutableSortedSet.Builder<String> files = ImmutableSortedSet.naturalOrder();
        InputStream fileList = getClass().getResourceAsStream(RESOURCE_PATH + "files.lst");
        if (fileList != null) {
            try (Scanner sc = new Scanner(fileList)) {
                while (sc.hasNextLine()) {
                    String fileName = sc.nextLine()
                        .trim();
                    if (!fileName.isEmpty()) files.add(fileName);
                }
            }
        }

        this.files = files.build();
    }

    @Override
    public boolean exists(String path) {
        return path != null && (path.isEmpty() || files.contains(path));
    }

    @Override
    public boolean isDirectory(String path) {
        return path != null && path.isEmpty();
    }

    @Override
    public void list(String path, List<String> contents) {
        // This mount is flat: only the root directory exists.
        if (isDirectory(path)) contents.addAll(files);
    }

    @Override
    public long getSize(String path) {
        if (path == null || !files.contains(path)) return 0;

        final Long cached = sizes.get(path);
        if (cached != null) return cached;

        try (InputStream stream = getClass().getResourceAsStream(RESOURCE_PATH + path)) {
            if (stream == null) return 0;
            final long length = ByteStreams.toByteArray(stream).length;
            sizes.put(path, length);
            return length;
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public InputStream openForRead(String path) throws IOException {
        if (path == null || !files.contains(path)) throw new IOException("No such file: " + path);

        final InputStream stream = getClass().getResourceAsStream(RESOURCE_PATH + path);
        if (stream == null) throw new IOException("No such file: " + path);
        return stream;
    }
}
