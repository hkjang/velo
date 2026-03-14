package io.velo.was.jsp.translator;

/**
 * Result of translating a JSP into Java servlet source code.
 */
public record TranslatedSource(
        String packageName,
        String className,
        String javaSource,
        String sourceJspPath
) {
    public String fullyQualifiedClassName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }
}
