package act.view;

import act.Act;
import act.app.ActionContext;
import act.mail.MailerContext;
import org.osgl.http.H;
import org.osgl.util.IO;

import java.util.Map;

/**
 * Base class for {@link Template} implementations
 */
public abstract class TemplateBase implements Template {

    @Override
    public void merge(ActionContext context) {
        Map<String, Object> renderArgs = context.renderArgs();
        exposeImplicitVariables(renderArgs, context);
        beforeRender(context);
        merge(renderArgs, context.resp());
    }

    @Override
    public String render(ActionContext context) {
        Map<String, Object> renderArgs = context.renderArgs();
        exposeImplicitVariables(renderArgs, context);
        beforeRender(context);
        return render(renderArgs);
    }

    @Override
    public String render(MailerContext context) {
        Map<String, Object> renderArgs = context.renderArgs();
        exposeImplicitVariables(renderArgs, context);
        beforeRender(context);
        return render(renderArgs);
    }

    /**
     * Sub class can implement this method to inject logic that needs to be done
     * before rendering happening
     *
     * @param context
     */
    protected void beforeRender(ActionContext context) {}

    /**
     * Sub class can implement this method to inject logic that needs to be done
     * before rendering happening
     *
     * @param context
     */
    protected void beforeRender(MailerContext context) {}

    protected void merge(Map<String, Object> renderArgs, H.Response response) {
        IO.writeContent(render(renderArgs), response.writer());
    }

    protected abstract String render(Map<String, Object> renderArgs);

    private void exposeImplicitVariables(Map<String, Object> renderArgs, ActionContext context) {
        for (ActionViewVarDef var : Act.viewManager().implicitActionViewVariables()) {
            renderArgs.put(var.name(), var.eval(context));
        }
    }


    private void exposeImplicitVariables(Map<String, Object> renderArgs, MailerContext context) {
        for (MailerViewVarDef var : Act.viewManager().implicitMailerViewVariables()) {
            renderArgs.put(var.name(), var.eval(context));
        }
    }
}
