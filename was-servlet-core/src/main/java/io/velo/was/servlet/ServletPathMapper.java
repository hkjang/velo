package io.velo.was.servlet;

import java.util.Set;

interface ServletPathMapper {

    ServletPathMatch resolve(Set<String> mappings, String applicationRelativePath);
}
