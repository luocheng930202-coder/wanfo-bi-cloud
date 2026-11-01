package com.wanfo.bi.datasource.snapshot;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;

/** In-memory Parquet InputFile for bounded object ingest (no local temp file). */
final class ByteArrayInputFile implements InputFile {
    private final byte[] data;

    ByteArrayInputFile(byte[] data) {
        this.data = data == null ? new byte[0] : data;
    }

    @Override
    public long getLength() {
        return data.length;
    }

    @Override
    public SeekableInputStream newStream() {
        return new SeekableInputStream() {
            private long pos = 0;

            @Override
            public void seek(long newPos) throws IOException {
                if (newPos < 0 || newPos > data.length) throw new IOException("seek out of range: " + newPos);
                pos = newPos;
            }

            @Override
            public long getPos() {
                return pos;
            }

            @Override
            public void readFully(byte[] bytes) throws IOException {
                readFully(bytes, 0, bytes.length);
            }

            @Override
            public void readFully(byte[] bytes, int start, int len) throws IOException {
                if (len < 0 || start < 0 || start + len > bytes.length) throw new IOException("bad buffer range");
                if (pos + len > data.length) throw new EOFException();
                System.arraycopy(data, (int) pos, bytes, start, len);
                pos += len;
            }

            @Override
            public void readFully(ByteBuffer buf) throws IOException {
                int remaining = buf.remaining();
                if (pos + remaining > data.length) throw new EOFException();
                buf.put(data, (int) pos, remaining);
                pos += remaining;
            }

            @Override
            public int read(ByteBuffer buf) throws IOException {
                int want = buf.remaining();
                if (want <= 0) return 0;
                if (pos >= data.length) return -1;
                int n = (int) Math.min(want, data.length - pos);
                buf.put(data, (int) pos, n);
                pos += n;
                return n;
            }

            @Override
            public int read() throws IOException {
                if (pos >= data.length) return -1;
                return data[(int) pos++] & 0xff;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len <= 0) return 0;
                if (pos >= data.length) return -1;
                int n = (int) Math.min(len, data.length - pos);
                System.arraycopy(data, (int) pos, b, off, n);
                pos += n;
                return n;
            }

            @Override
            public long skip(long n) {
                long skipped = Math.min(n, data.length - pos);
                if (skipped < 0) return 0;
                pos += skipped;
                return skipped;
            }

            @Override
            public int available() {
                return (int) Math.min(Integer.MAX_VALUE, data.length - pos);
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }
}
