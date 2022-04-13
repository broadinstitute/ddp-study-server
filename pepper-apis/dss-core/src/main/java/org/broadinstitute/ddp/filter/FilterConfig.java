package org.broadinstitute.ddp.filter;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import javax.servlet.ServletContext;

public class FilterConfig implements javax.servlet.FilterConfig {

    private final String filterName;
    private final Map<String, String> initParams;

    public FilterConfig(String filterName,
                        Map<String, String> initParams) {

        this.filterName = filterName;
        this.initParams = initParams;
    }

    @Override
    public String getFilterName() {
        return filterName;
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public String getInitParameter(String s) {
        return initParams.get(s);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParams.keySet());
    }
}
