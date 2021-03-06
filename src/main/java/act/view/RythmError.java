package act.view;

import act.app.SourceInfo;
import org.osgl.util.C;
import org.rythmengine.exception.RythmException;

import java.util.List;

public class RythmError extends ActServerError {

    private SourceInfo templateInfo;

    public RythmError(RythmException t) {
        super(t);
    }

    public RythmException rythmException() {
        return (RythmException) getCause();
    }

    public SourceInfo templateSourceInfo() {
        return templateInfo;
    }

    @Override
    protected void populateSourceInfo(Throwable t) {
        RythmException re = (RythmException) t;
        sourceInfo = new RythmSourceInfo(re, true);
        templateInfo = new RythmSourceInfo(re, false);
    }

    private static class RythmSourceInfo implements SourceInfo {

        RythmSourceInfo(RythmException e, boolean javaSource) {
            fileName = e.templateName;
            if (javaSource) {
                lineNumber = e.javaLineNumber;
                lines = C.listOf(e.javaSource.split("[\n]"));
            } else {
                lineNumber = e.templateLineNumber;
                lines = C.listOf(e.templateSource.split("[\n]"));
            }
        }

        private String fileName;
        private List<String> lines;
        private int lineNumber;

        @Override
        public String fileName() {
            return fileName;
        }

        @Override
        public List<String> lines() {
            return lines;
        }

        @Override
        public Integer lineNumber() {
            return lineNumber;
        }

        @Override
        public boolean isSourceAvailable() {
            return true;
        }
    }
}
