package org.osgl.oms.route;

import org.osgl.http.H;
import org.osgl.http.util.Path;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;
import org.osgl.util.Unsafe;

import java.util.Iterator;
import java.util.List;

/**
 * {@code RouteTableRouterBuilder} take a list of route map definition line and
 * build the router. The line of a route map definition should look like:
 *
 * <pre>
 * [http-method] [url-path] [action-definition]
 * </pre>
 *
 * Where http-method could be one of the following:
 * <ul>
 * <li>GET</li>
 * <li>POST</li>
 * <li>PUT</li>
 * <li>DELETE</li>
 * </ul>
 *
 * url-path defines the incoming URL path, and it could
 * be either static or dynamic. For example,
 *
 * <pre>
 * # home
 * /
 *
 * # order list
 * /order
 *
 * # (dynamic) access to a certain order by ID
 * /order/{id}
 *
 * # (dynamic) access to a user by ID with regex spec
 * /user/{&lt;[1-9]{5}&gt;id}
 * </pre>
 *
 * action-definition could be in either built-in action
 * or controller action method.
 *
 * <p>Built-in action definition should be in a format of
 * <code>[directive]: [payload]</code>, for example
 * </p>
 *
 * <ul>
 * <li>
 * Echo - write back a text message directly
 * <pre>
 * echo: hello world!
 * </pre>
 * </li>
 * <li>
 * Redirect - send permanent redirect in the response
 * <pre>
 * redirect: http://www.google.com
 * </pre>
 * </li>
 * <li>
 * Static file directory handler - fetch files in a local directory
 * <pre>
 * staticDir: /public
 * </pre>
 * </li>
 * <li>
 * Static file locator - fetch specified file on request
 * <pre>
 * staticFile: /public/js/jquery.js
 * </pre>
 * </li>
 * </ul>
 *
 */
public class RouteTableRouterBuilder implements RouterBuilder {

    public static final String ROUTES_FILE = "routes";

    private List<String> lines;

    public RouteTableRouterBuilder(List<String> lines) {
        E.NPE(lines);
        this.lines = lines;
    }

    public RouteTableRouterBuilder(String... lines) {
        E.illegalArgumentIf(lines.length == 0, "Empty route configuration file lines");
        this.lines = C.listOf(lines);
    }

    @Override
    public void build(Router router) {
        int lineNo = lines.size();
        for (int i = 0; i < lineNo; ++i) {
            String line = lines.get(i).trim();
            if (line.startsWith("#")) continue;
            if (S.blank(line)) continue;
            process(line, router);
        }
    }

    private void process(String line, Router router) {
        Iterator<CharSequence> itr = Path.tokenizer(Unsafe.bufOf(line), 0, ' ', '\u0000');
        final String UNKNOWN = S.fmt("route configuration not recognized: %s", line);
        CharSequence method = null, path = null;
        StringBuilder action = new StringBuilder(line.length());
        if (itr.hasNext()) {
            method = itr.next();
        } else {
            E.illegalArgumentIf(true, UNKNOWN);
        }
        if (itr.hasNext()) {
            path = itr.next();
        } else {
            E.illegalArgumentIf(true, UNKNOWN);
        }
        E.illegalArgumentIf(!itr.hasNext(), UNKNOWN);
        action.append(itr.next());
        while (itr.hasNext()) {
            action.append(" ").append(itr.next());
        }
        if ("*".contentEquals(method)) {
            for (H.Method m : Router.supportedHttpMethods()) {
                router.addMapping(m, path, action);
            }
        } else {
            H.Method m = H.Method.valueOfIgnoreCase(method.toString());
            router.addMapping(m, path, action);
        }
    }

}