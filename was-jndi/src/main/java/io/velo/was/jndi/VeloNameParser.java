package io.velo.was.jndi;

import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingException;

/**
 * Simple name parser that treats names as composite names with "/" separator.
 */
final class VeloNameParser implements NameParser {

    static final VeloNameParser INSTANCE = new VeloNameParser();

    private VeloNameParser() {}

    @Override
    public Name parse(String name) throws NamingException {
        return new CompositeName(name);
    }
}
