package act.handler.builtin.cli;

import act.Act;
import act.app.App;
import act.app.CliContext;
import act.cli.CliError;
import act.cli.CommandExecutor;
import act.cli.bytecode.ReflectedCommandExecutor;
import act.cli.meta.CommandMethodMetaInfo;
import act.cli.meta.CommanderClassMetaInfo;
import act.handler.CliHandlerBase;
import act.util.PropertySpec;
import org.osgl.$;
import org.osgl.logging.L;
import org.osgl.logging.Logger;
import org.osgl.util.S;

import java.util.List;

public final class CliHandlerProxy extends CliHandlerBase {

    private static Logger logger = L.get(CliHandlerProxy.class);

    private App app;
    private CommandMethodMetaInfo methodMetaInfo;
    private CommanderClassMetaInfo classMetaInfo;

    private volatile CommandExecutor executor = null;

    public CliHandlerProxy(CommanderClassMetaInfo classMetaInfo, CommandMethodMetaInfo metaInfo, App app) {
        this.methodMetaInfo = $.notNull(metaInfo);
        this.classMetaInfo = $.notNull(classMetaInfo);
        this.app = $.notNull(app);
    }

    @Override
    protected void releaseResources() {
        if (null != executor) {
            executor.destroy();
            executor = null;
        }
    }

    @Override
    public boolean appliedIn(Act.Mode mode) {
        return mode == Act.Mode.DEV || mode == methodMetaInfo.mode();
    }

    @Override
    public void handle(CliContext context) {
        try {
            ensureAgentsReady();
            saveCommandPath(context);
            Object result = _handle(context);
            onResult(result, context);
        } catch (CliError error) {
            context.println(error.getMessage());
        } catch (Exception e) {
            context.println("Error processing command: " + e.getMessage());
            logger.error(e, "Error handling request");
        }
    }

    @Override
    public $.T2<String, String> commandLine(String commandName) {
        return methodMetaInfo.commandLine(commandName, classMetaInfo, app.classLoader());
    }

    @Override
    public List<$.T2<String, String>> options() {
        return methodMetaInfo.options(classMetaInfo, app.classLoader());
    }

    @SuppressWarnings("unchecked")
    private void onResult(Object result, CliContext context) {
        if (null == result) {
            return;
        }
        PropertySpec.MetaInfo filter = methodMetaInfo.propertySpec();
        methodMetaInfo.view().print(result, filter, context);
    }

    private void ensureAgentsReady() {
        if (null == executor) {
            synchronized (this) {
                if (null == executor) {
                    generateExecutor();
                }
            }
        }
    }

    // could be used by View to resolve default path to template
    private void saveCommandPath(CliContext context) {
        StringBuilder sb = S.builder(methodMetaInfo.fullName());
        String path = sb.toString();
        context.commandPath(path);
    }

    private void generateExecutor() {
        executor = new ReflectedCommandExecutor(classMetaInfo, methodMetaInfo, app);
    }


    private Object _handle(CliContext context) {
        return executor.execute(context);
    }

    @Override
    public String toString() {
        return methodMetaInfo.fullName();
    }

}
