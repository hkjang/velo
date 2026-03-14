package io.velo.was.jndi;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;

/**
 * JNDI SPI factory that creates {@link VeloNamingContext} instances.
 * <p>
 * Maintains a singleton root context so that all lookups within the same
 * classloader share the same namespace (standard WAS behavior).
 * <p>
 * Activation:
 * <pre>
 *   System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
 *       "io.velo.was.jndi.VeloInitialContextFactory");
 * </pre>
 * Or via {@code jndi.properties} on the classpath.
 */
public class VeloInitialContextFactory implements InitialContextFactory {

    private static final VeloNamingContext ROOT = new VeloNamingContext();

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        return ROOT;
    }

    /**
     * Returns the singleton root context.
     * Convenient for programmatic binding without going through
     * {@code new InitialContext()}.
     */
    public static VeloNamingContext root() {
        return ROOT;
    }
}
