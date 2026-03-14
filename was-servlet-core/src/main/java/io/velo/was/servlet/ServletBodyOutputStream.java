package io.velo.was.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ServletBodyOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        throw new UnsupportedOperationException("Async IO is not implemented yet");
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    public byte[] toByteArray() {
        return delegate.toByteArray();
    }

    public void reset() {
        delegate.reset();
    }
}
