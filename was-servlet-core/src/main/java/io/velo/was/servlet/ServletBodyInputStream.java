package io.velo.was.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ServletBodyInputStream extends ServletInputStream {

    private final ByteArrayInputStream delegate;

    public ServletBodyInputStream(byte[] body) {
        this.delegate = new ByteArrayInputStream(body);
    }

    @Override
    public boolean isFinished() {
        return delegate.available() == 0;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("Async IO is not implemented yet");
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }
}

