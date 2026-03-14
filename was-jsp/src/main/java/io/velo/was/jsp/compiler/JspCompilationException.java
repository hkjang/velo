package io.velo.was.jsp.compiler;

/**
 * Thrown when JSP compilation fails.
 */
public class JspCompilationException extends Exception {

    private final String sourceFile;
    private final long line;
    private final long column;

    public JspCompilationException(String message, String sourceFile, long line, long column) {
        super(message);
        this.sourceFile = sourceFile;
        this.line = line;
        this.column = column;
    }

    public JspCompilationException(String message, Throwable cause) {
        super(message, cause);
        this.sourceFile = "";
        this.line = -1;
        this.column = -1;
    }

    public String sourceFile() { return sourceFile; }
    public long line() { return line; }
    public long column() { return column; }
}
