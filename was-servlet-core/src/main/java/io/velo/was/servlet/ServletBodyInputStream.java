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
        if (readListener == null) {
            throw new NullPointerException("ReadListener must not be null");
        }
        // Since the body is already fully buffered in memory, we can immediately
        // notify the listener that data is available and reading is complete.
        try {
            readListener.onDataAvailable();
            if (isFinished()) {
                readListener.onAllDataRead();
            }
        } catch (IOException e) {
            readListener.onError(e);
        }
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }
}

