package io.velo.was.jsp.runtime;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight EL expression evaluator supporting:
 * <ul>
 *     <li>Variable references: {@code ${name}}</li>
 *     <li>Property access: {@code ${user.name}}, {@code ${map['key']}}</li>
 *     <li>Implicit objects: requestScope, sessionScope, applicationScope, param, header</li>
 *     <li>Basic arithmetic: {@code +, -, *, /, %}</li>
 *     <li>Comparison: {@code ==, !=, <, >, <=, >=}</li>
 *     <li>Logical: {@code &&, ||, !}</li>
 *     <li>Empty operator: {@code ${empty list}}</li>
 *     <li>Ternary: {@code ${condition ? a : b}}</li>
 *     <li>String literals: {@code ${'text'}}, {@code ${"text"}}</li>
 * </ul>
 */
public final class SimpleElEvaluator {

    private SimpleElEvaluator() {}

    /**
     * Evaluates an EL expression string. This is the main entry point called
     * from translated JSP code.
     */
    public static Object evaluate(String expression, HttpServletRequest request,
                                   HttpSession session, ServletContext application) {
        if (expression == null || expression.isBlank()) return "";

        String expr = expression.trim();

        // Ternary operator
        int ternaryIdx = findTernary(expr);
        if (ternaryIdx > 0) {
            String condition = expr.substring(0, ternaryIdx).trim();
            String rest = expr.substring(ternaryIdx + 1).trim();
            int colonIdx = rest.indexOf(':');
            if (colonIdx > 0) {
                String trueVal = rest.substring(0, colonIdx).trim();
                String falseVal = rest.substring(colonIdx + 1).trim();
                boolean condResult = toBoolean(evaluate(condition, request, session, application));
                return evaluate(condResult ? trueVal : falseVal, request, session, application);
            }
        }

        // Logical OR
        int orIdx = findOperator(expr, "||");
        if (orIdx > 0) {
            return toBoolean(evaluate(expr.substring(0, orIdx).trim(), request, session, application))
                    || toBoolean(evaluate(expr.substring(orIdx + 2).trim(), request, session, application));
        }

        // Logical AND
        int andIdx = findOperator(expr, "&&");
        if (andIdx > 0) {
            return toBoolean(evaluate(expr.substring(0, andIdx).trim(), request, session, application))
                    && toBoolean(evaluate(expr.substring(andIdx + 2).trim(), request, session, application));
        }

        // Comparison operators
        for (String op : new String[]{"==", "!=", "<=", ">=", "<", ">"}) {
            int idx = findOperator(expr, op);
            if (idx > 0) {
                Object left = evaluate(expr.substring(0, idx).trim(), request, session, application);
                Object right = evaluate(expr.substring(idx + op.length()).trim(), request, session, application);
                return compareValues(left, right, op);
            }
        }

        // Arithmetic + -
        for (String op : new String[]{"+", "-"}) {
            int idx = findOperatorReverse(expr, op);
            if (idx > 0) {
                Object left = evaluate(expr.substring(0, idx).trim(), request, session, application);
                Object right = evaluate(expr.substring(idx + 1).trim(), request, session, application);
                return arithmetic(left, right, op);
            }
        }

        // Arithmetic * / %
        for (String op : new String[]{"*", "/", "%"}) {
            int idx = findOperatorReverse(expr, op);
            if (idx > 0) {
                Object left = evaluate(expr.substring(0, idx).trim(), request, session, application);
                Object right = evaluate(expr.substring(idx + 1).trim(), request, session, application);
                return arithmetic(left, right, op);
            }
        }

        // Not operator
        if (expr.startsWith("!") || expr.startsWith("not ")) {
            String inner = expr.startsWith("!") ? expr.substring(1).trim() : expr.substring(4).trim();
            return !toBoolean(evaluate(inner, request, session, application));
        }

        // Empty operator
        if (expr.startsWith("empty ")) {
            Object val = evaluate(expr.substring(6).trim(), request, session, application);
            return isEmpty(val);
        }

        // String literal
        if ((expr.startsWith("'") && expr.endsWith("'")) ||
            (expr.startsWith("\"") && expr.endsWith("\""))) {
            return expr.substring(1, expr.length() - 1);
        }

        // Numeric literal
        try { return Long.parseLong(expr); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(expr); } catch (NumberFormatException ignored) {}

        // Boolean literal
        if ("true".equalsIgnoreCase(expr)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(expr)) return Boolean.FALSE;
        if ("null".equalsIgnoreCase(expr)) return null;

        // Property chain: variable.prop1.prop2 or variable['key']
        return resolvePropertyChain(expr, request, session, application);
    }

    private static Object resolvePropertyChain(String expr,
                                                HttpServletRequest request,
                                                HttpSession session,
                                                ServletContext application) {
        String[] parts = splitPropertyChain(expr);
        Object current = resolveVariable(parts[0], request, session, application);
        for (int i = 1; i < parts.length && current != null; i++) {
            current = getProperty(current, parts[i]);
        }
        return current != null ? current : "";
    }

    private static String[] splitPropertyChain(String expr) {
        // Split by dots but respect bracket notation: user.name -> [user, name]
        // user['name'] -> [user, name]
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '.') {
                if (!current.isEmpty()) parts.add(current.toString());
                current = new StringBuilder();
                i++;
            } else if (c == '[') {
                if (!current.isEmpty()) parts.add(current.toString());
                current = new StringBuilder();
                i++; // skip [
                // Find matching ]
                if (i < expr.length() && (expr.charAt(i) == '\'' || expr.charAt(i) == '"')) {
                    char quote = expr.charAt(i);
                    i++; // skip quote
                    while (i < expr.length() && expr.charAt(i) != quote) {
                        current.append(expr.charAt(i++));
                    }
                    i++; // skip closing quote
                } else {
                    while (i < expr.length() && expr.charAt(i) != ']') {
                        current.append(expr.charAt(i++));
                    }
                }
                if (!current.isEmpty()) parts.add(current.toString());
                current = new StringBuilder();
                i++; // skip ]
            } else {
                current.append(c);
                i++;
            }
        }
        if (!current.isEmpty()) parts.add(current.toString());
        return parts.toArray(String[]::new);
    }

    @SuppressWarnings("unchecked")
    private static Object resolveVariable(String name, HttpServletRequest request,
                                           HttpSession session, ServletContext application) {
        return switch (name) {
            case "param" -> asMap(request.getParameterMap());
            case "paramValues" -> request.getParameterMap();
            case "header" -> {
                Map<String, String> headers = new HashMap<>();
                var names = request.getHeaderNames();
                if (names != null) {
                    while (names.hasMoreElements()) {
                        String h = names.nextElement();
                        headers.put(h, request.getHeader(h));
                    }
                }
                yield headers;
            }
            case "requestScope" -> {
                Map<String, Object> map = new HashMap<>();
                var attrNames = request.getAttributeNames();
                while (attrNames.hasMoreElements()) {
                    String n = attrNames.nextElement();
                    map.put(n, request.getAttribute(n));
                }
                yield map;
            }
            case "sessionScope" -> {
                if (session == null) yield Collections.emptyMap();
                Map<String, Object> map = new HashMap<>();
                var attrNames2 = session.getAttributeNames();
                while (attrNames2.hasMoreElements()) {
                    String n = attrNames2.nextElement();
                    map.put(n, session.getAttribute(n));
                }
                yield map;
            }
            case "applicationScope" -> {
                Map<String, Object> map = new HashMap<>();
                var attrNames3 = application.getAttributeNames();
                while (attrNames3.hasMoreElements()) {
                    String n = attrNames3.nextElement();
                    map.put(n, application.getAttribute(n));
                }
                yield map;
            }
            default -> {
                // Search scopes: page (request attributes), request, session, application
                Object val = request.getAttribute(name);
                if (val != null) yield val;
                if (session != null) {
                    val = session.getAttribute(name);
                    if (val != null) yield val;
                }
                val = application.getAttribute(name);
                if (val != null) yield val;
                // Check request parameters
                val = request.getParameter(name);
                yield val;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Object getProperty(Object obj, String property) {
        if (obj instanceof Map<?,?> map) {
            return map.get(property);
        }
        // Try getter
        String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (Exception ignored) {}

        // Try isXxx for boolean
        String isName = "is" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            Method getter = obj.getClass().getMethod(isName);
            return getter.invoke(obj);
        } catch (Exception ignored) {}

        return null;
    }

    private static Map<String, String> asMap(Map<String, String[]> paramMap) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
            if (e.getValue().length > 0) {
                result.put(e.getKey(), e.getValue()[0]);
            }
        }
        return result;
    }

    private static boolean toBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean b) return b;
        if (obj instanceof Number n) return n.doubleValue() != 0;
        if (obj instanceof String s) return !s.isEmpty();
        return true;
    }

    private static boolean isEmpty(Object val) {
        if (val == null) return true;
        if (val instanceof String s) return s.isEmpty();
        if (val instanceof java.util.Collection<?> c) return c.isEmpty();
        if (val instanceof Map<?,?> m) return m.isEmpty();
        if (val.getClass().isArray()) return java.lang.reflect.Array.getLength(val) == 0;
        return false;
    }

    private static Object compareValues(Object left, Object right, String op) {
        if (left instanceof Number ln && right instanceof Number rn) {
            double l = ln.doubleValue(), r = rn.doubleValue();
            return switch (op) {
                case "==" -> l == r;
                case "!=" -> l != r;
                case "<" -> l < r;
                case ">" -> l > r;
                case "<=" -> l <= r;
                case ">=" -> l >= r;
                default -> false;
            };
        }
        if ("==".equals(op)) return java.util.Objects.equals(left, right);
        if ("!=".equals(op)) return !java.util.Objects.equals(left, right);
        if (left instanceof Comparable<?> cl && right != null) {
            try {
                @SuppressWarnings("unchecked")
                int cmp = ((Comparable<Object>) cl).compareTo(right);
                return switch (op) {
                    case "<" -> cmp < 0;
                    case ">" -> cmp > 0;
                    case "<=" -> cmp <= 0;
                    case ">=" -> cmp >= 0;
                    default -> false;
                };
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static Object arithmetic(Object left, Object right, String op) {
        double l = toDouble(left), r = toDouble(right);
        return switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "/" -> r != 0 ? l / r : 0.0;
            case "%" -> r != 0 ? l % r : 0.0;
            default -> 0.0;
        };
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    private static int findTernary(String expr) {
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '?' && depth == 0) return i;
        }
        return -1;
    }

    private static int findOperator(String expr, String op) {
        int depth = 0;
        boolean inSingleQuote = false, inDoubleQuote = false;
        for (int i = 0; i <= expr.length() - op.length(); i++) {
            char c = expr.charAt(i);
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
            else if (c == '(' && !inSingleQuote && !inDoubleQuote) depth++;
            else if (c == ')' && !inSingleQuote && !inDoubleQuote) depth--;
            else if (depth == 0 && !inSingleQuote && !inDoubleQuote &&
                     expr.startsWith(op, i)) {
                // Avoid matching <= >= != as part of < > !
                return i;
            }
        }
        return -1;
    }

    private static int findOperatorReverse(String expr, String op) {
        int depth = 0;
        boolean inSingleQuote = false, inDoubleQuote = false;
        for (int i = expr.length() - op.length(); i >= 1; i--) {
            char c = expr.charAt(i);
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
            else if (c == ')' && !inSingleQuote && !inDoubleQuote) depth++;
            else if (c == '(' && !inSingleQuote && !inDoubleQuote) depth--;
            else if (depth == 0 && !inSingleQuote && !inDoubleQuote &&
                     expr.startsWith(op, i)) {
                return i;
            }
        }
        return -1;
    }
}
