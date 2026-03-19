package io.velo.was.deploy;

import javax.xml.XMLConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Parses a WEB-INF/web.xml deployment descriptor into a {@link WebXmlDescriptor}.
 */
public final class WebXmlParser {

    private static final Logger log = LoggerFactory.getLogger(WebXmlParser.class);

    private WebXmlParser() {
    }

    /**
     * Parses the web.xml file and returns a descriptor.
     *
     * @param webXmlPath path to web.xml
     * @return parsed descriptor
     * @throws Exception if parsing fails
     */
    public static WebXmlDescriptor parse(Path webXmlPath) throws Exception {
        if (!Files.exists(webXmlPath)) {
            log.info("No web.xml found at {}, using empty descriptor", webXmlPath);
            return WebXmlDescriptor.empty();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

        Document document;
        try (InputStream in = Files.newInputStream(webXmlPath)) {
            document = builder.parse(in);
        }

        Element root = document.getDocumentElement();

        String displayName = textContent(root, "display-name");
        boolean metadataComplete = Boolean.parseBoolean(root.getAttribute("metadata-complete"));
        Map<String, String> contextParams = parseContextParams(root);
        List<WebXmlDescriptor.ServletDef> servlets = parseServlets(root);
        List<WebXmlDescriptor.ServletMapping> servletMappings = parseServletMappings(root);
        List<WebXmlDescriptor.FilterDef> filters = parseFilters(root);
        List<WebXmlDescriptor.FilterMapping> filterMappings = parseFilterMappings(root);
        List<String> listeners = parseListeners(root);
        List<String> welcomeFiles = parseWelcomeFiles(root);
        List<WebXmlDescriptor.ErrorPageDef> errorPages = parseErrorPages(root);

        WebXmlDescriptor descriptor = new WebXmlDescriptor(
                displayName, metadataComplete, contextParams, servlets, servletMappings,
                filters, filterMappings, listeners, welcomeFiles, errorPages);

        log.info("Parsed web.xml: servlets={}, filters={}, listeners={}, mappings={}, errorPages={}",
                servlets.size(), filters.size(), listeners.size(), servletMappings.size(), errorPages.size());
        return descriptor;
    }

    private static Map<String, String> parseContextParams(Element root) {
        Map<String, String> params = new LinkedHashMap<>();
        NodeList nodes = root.getElementsByTagName("context-param");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = textContent(element, "param-name");
            String value = textContent(element, "param-value");
            if (name != null && value != null) {
                params.put(name, value);
            }
        }
        return params;
    }

    private static List<WebXmlDescriptor.ServletDef> parseServlets(Element root) {
        List<WebXmlDescriptor.ServletDef> servlets = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("servlet");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = textContent(element, "servlet-name");
            String className = textContent(element, "servlet-class");
            Map<String, String> initParams = parseInitParams(element);
            String loadOnStartup = textContent(element, "load-on-startup");
            String asyncSupported = textContent(element, "async-supported");
            if (name != null && className != null) {
                servlets.add(new WebXmlDescriptor.ServletDef(
                        name, className, initParams,
                        loadOnStartup != null ? Integer.parseInt(loadOnStartup.trim()) : -1,
                        "true".equalsIgnoreCase(asyncSupported)));
            }
        }
        return servlets;
    }

    private static List<WebXmlDescriptor.ServletMapping> parseServletMappings(Element root) {
        List<WebXmlDescriptor.ServletMapping> mappings = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("servlet-mapping");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = textContent(element, "servlet-name");
            List<String> patterns = textContents(element, "url-pattern");
            if (name != null) {
                for (String pattern : patterns) {
                    mappings.add(new WebXmlDescriptor.ServletMapping(name, pattern));
                }
            }
        }
        return mappings;
    }

    private static List<WebXmlDescriptor.FilterDef> parseFilters(Element root) {
        List<WebXmlDescriptor.FilterDef> filters = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("filter");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = textContent(element, "filter-name");
            String className = textContent(element, "filter-class");
            Map<String, String> initParams = parseInitParams(element);
            String asyncSupported = textContent(element, "async-supported");
            if (name != null && className != null) {
                filters.add(new WebXmlDescriptor.FilterDef(
                        name, className, initParams,
                        "true".equalsIgnoreCase(asyncSupported)));
            }
        }
        return filters;
    }

    private static List<WebXmlDescriptor.FilterMapping> parseFilterMappings(Element root) {
        List<WebXmlDescriptor.FilterMapping> mappings = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("filter-mapping");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = textContent(element, "filter-name");
            List<String> urlPatterns = textContents(element, "url-pattern");
            List<String> servletNames = textContents(element, "servlet-name");
            List<String> dispatchers = textContents(element, "dispatcher");
            if (name != null) {
                for (String pattern : urlPatterns) {
                    mappings.add(new WebXmlDescriptor.FilterMapping(name, pattern, null,
                            dispatchers.isEmpty() ? List.of("REQUEST") : dispatchers));
                }
                for (String servletName : servletNames) {
                    mappings.add(new WebXmlDescriptor.FilterMapping(name, null, servletName,
                            dispatchers.isEmpty() ? List.of("REQUEST") : dispatchers));
                }
            }
        }
        return mappings;
    }

    private static List<String> parseListeners(Element root) {
        List<String> listeners = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("listener");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String className = textContent(element, "listener-class");
            if (className != null) {
                listeners.add(className);
            }
        }
        return listeners;
    }

    private static List<String> parseWelcomeFiles(Element root) {
        List<String> files = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("welcome-file-list");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            files.addAll(textContents(element, "welcome-file"));
        }
        return files;
    }

    private static List<WebXmlDescriptor.ErrorPageDef> parseErrorPages(Element root) {
        List<WebXmlDescriptor.ErrorPageDef> errorPages = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("error-page");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String errorCodeText = textContent(element, "error-code");
            String exceptionType = textContent(element, "exception-type");
            String location = textContent(element, "location");
            Integer errorCode = errorCodeText == null ? null : Integer.parseInt(errorCodeText.trim());
            if (location != null && (errorCode != null || exceptionType != null)) {
                errorPages.add(new WebXmlDescriptor.ErrorPageDef(errorCode, exceptionType, location));
            }
        }
        return errorPages;
    }

    private static Map<String, String> parseInitParams(Element parent) {
        Map<String, String> params = new LinkedHashMap<>();
        NodeList nodes = parent.getElementsByTagName("init-param");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = textContent(element, "param-name");
            String value = textContent(element, "param-value");
            if (name != null && value != null) {
                params.put(name, value);
            }
        }
        return params;
    }

    private static String textContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private static List<String> textContents(Element parent, String tagName) {
        List<String> contents = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            String text = nodes.item(i).getTextContent();
            if (text != null && !text.isBlank()) {
                contents.add(text.trim());
            }
        }
        return contents;
    }
}
