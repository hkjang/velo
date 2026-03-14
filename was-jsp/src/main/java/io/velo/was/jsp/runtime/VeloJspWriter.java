package io.velo.was.jsp.runtime;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * A JspWriter-like buffered writer for JSP output.
 * Wraps the {@link HttpServletResponse#getWriter()} and adds buffering support.
 */
public class VeloJspWriter extends Writer {

    private final PrintWriter delegate;
    private final int bufferSize;
    private final boolean autoFlush;
    private final StringBuilder buffer;

    public VeloJspWriter(HttpServletResponse response, int bufferSize, boolean autoFlush) throws IOException {
        this.delegate = response.getWriter();
        this.bufferSize = bufferSize;
        this.autoFlush = autoFlush;
        this.buffer = bufferSize > 0 ? new StringBuilder(bufferSize) : null;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (buffer != null) {
            buffer.append(cbuf, off, len);
            if (autoFlush && buffer.length() >= bufferSize) {
                flushBuffer();
            }
        } else {
            delegate.write(cbuf, off, len);
        }
    }

    @Override
    public void write(String str) throws IOException {
        if (buffer != null) {
            buffer.append(str);
            if (autoFlush && buffer.length() >= bufferSize) {
                flushBuffer();
            }
        } else {
            delegate.write(str);
        }
    }

    public void print(Object obj) throws IOException {
        write(obj != null ? obj.toString() : "null");
    }

    public void println(Object obj) throws IOException {
        print(obj);
        write("\n");
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getRemaining() {
        if (buffer == null) return 0;
        return Math.max(0, bufferSize - buffer.length());
    }

    public void clearBuffer() {
        if (buffer != null) {
            buffer.setLength(0);
        }
    }

    @Override
    public void flush() throws IOException {
        flushBuffer();
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        flushBuffer();
        delegate.close();
    }

    private void flushBuffer() {
        if (buffer != null && !buffer.isEmpty()) {
            delegate.write(buffer.toString());
            buffer.setLength(0);
        }
    }
}
