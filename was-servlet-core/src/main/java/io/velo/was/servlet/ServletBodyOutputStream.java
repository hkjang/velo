package io.velo.was.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ServletBodyOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    private boolean closed;

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        if (writeListener == null) {
            throw new NullPointerException("WriteListener must not be null");
        }
        // Since the output is buffered in memory, writing is always ready.
        // Notify the listener immediately that it can write.
        try {
            writeListener.onWritePossible();
        } catch (IOException e) {
            writeListener.onError(e);
        }
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        delegate.write(b);
    }

    public byte[] toByteArray() {
        return delegate.toByteArray();
    }

    public void reset() {
        delegate.reset();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        delegate.close();
    }
}
