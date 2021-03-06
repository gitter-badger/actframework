package act.cli;

import act.Act;
import act.app.App;
import act.app.AppServiceBase;
import act.cli.builtin.Exit;
import act.cli.builtin.Help;
import act.cli.meta.CommandMethodMetaInfo;
import act.cli.meta.CommanderClassMetaInfo;
import act.handler.CliHandler;
import act.handler.builtin.cli.CliHandlerProxy;
import org.osgl.Osgl;
import org.osgl.logging.LogManager;
import org.osgl.logging.Logger;
import org.osgl.util.C;
import org.osgl.util.E;

import java.util.List;
import java.util.Map;

/**
 * Dispatch console command to CLI command handler
 */
public class CliDispatcher extends AppServiceBase<CliDispatcher> {

    private static Logger logger = LogManager.get(CliDispatcher.class);

    private Map<String, CliHandler> registry = C.newMap();

    public CliDispatcher(App app) {
        super(app);
        registerBuiltInHandlers();
    }

    public CliDispatcher registerCommandHandler(String command, CommandMethodMetaInfo methodMetaInfo, CommanderClassMetaInfo classMetaInfo) {
        if (registry.containsKey(command)) {
            throw E.invalidConfiguration("Command %s already registered", command);
        }
        addToRegistry(command, new CliHandlerProxy(classMetaInfo, methodMetaInfo, app()));
        logger.debug("Command registered: %s", command);
        return this;
    }

    public boolean registered(String command) {
        return registry.containsKey(command);
    }

    public CliHandler handler(String command) {
        CliHandler handler = registry.get(command);
        if (null == handler && !command.startsWith("act.")) {
            handler = registry.get("act." + command);
        }

        Act.Mode mode = Act.mode();
        if (null != handler && handler.appliedIn(mode)) {
            return handler;
        }

        return null;
    }

    /**
     * Returns all commands in alphabetic order
     * @return the list of commands
     */
    public List<String> commands(boolean sys, boolean app) {
        C.List<String> list = C.newList();
        Act.Mode mode = Act.mode();
        boolean all = !sys && !app;
        for (String s : registry.keySet()) {
            boolean isSysCmd = s.startsWith("act.");
            if (isSysCmd && !sys && !all) {
                continue;
            }
            if (!isSysCmd && !app && !all) {
                continue;
            }
            CliHandler h = registry.get(s);
            if (h.appliedIn(mode)) {
                list.add(s);
            }
        }
        return list.sorted(new Osgl.Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                boolean b1 = (o1.startsWith("act."));
                boolean b2 = (o2.startsWith("act."));
                if (b1 & !b2) {
                    return -1;
                }
                if (!b1 & b2) {
                    return 1;
                }
                return o1.compareTo(o2);
            }
        });
    }

    @Override
    protected void releaseResources() {
        registry.clear();
    }

    private void addToRegistry(String name, CliHandler handler) {
        registry.put(name, handler);
        Help.updateMaxWidth(name.length());
    }

    private void registerBuiltInHandlers() {
        addToRegistry("act.exit", Exit.INSTANCE);
        addToRegistry("act.quit", Exit.INSTANCE);
        addToRegistry("act.bye", Exit.INSTANCE);
        addToRegistry("act.help", Help.INSTANCE);
    }
}
