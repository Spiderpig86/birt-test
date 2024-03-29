package org.eclipse.birt.spring.core;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportEngineFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

/**
 * Spring {@link FactoryBean} for the shared, singleton instance of
 * the {@link IReportEngine report engine}. There should be one {@link IReportEngine} per JVM.
 *
 * @author Jason Weathersby
 * @author Josh Long
 */
public class BirtEngineFactory implements FactoryBean<IReportEngine>, ApplicationContextAware, DisposableBean, InitializingBean {

    public static final String DEFAULT_SPRING_APPLICATION_CONTEXT_KEY = "spring";

    /**
     * Attribute under which the Spring {@link ApplicationContext application context} is exposed for BIRT reports.
     */
    private String exposedSpringApplicationContextKey = DEFAULT_SPRING_APPLICATION_CONTEXT_KEY;

    // guard the engine reference initialization
    private final Object monitor = new Object();

    // we need this reference to expose it to BIRT
    private ApplicationContext context;

    // the reference
    private IReportEngine engine;

    // the directory where the logging should be stored.
    private File logDirectory;

    // what level should logging be done at?
    private Level logLevel;

    public void setExposedSpringApplicationContextKey(String exposedSpringApplicationContextKey) {
        this.exposedSpringApplicationContextKey = exposedSpringApplicationContextKey;
    }

    public void setLogDirectory(Resource resource) {
        try {
            File file = resource.getFile();
            setLogDirectory(file);
        } catch (IOException e) {
            throw new RuntimeException("couldn't set the log directory");
        }
    }

    public void setLogLevel(Level ll) {
        this.logLevel = ll;
    }

    public void setLogDirectory(File f) {
        this.logDirectory = f;
    }

    public void setApplicationContext(ApplicationContext ctx) {
        this.context = ctx;
    }

    public boolean isSingleton() {
        return true;
    }

    public void destroy() throws Exception {
        engine.destroy();
        Platform.shutdown();
    }

    @SuppressWarnings("unchecked")
    public IReportEngine getObject() {
        synchronized (this.monitor) {

            if (this.engine != null)
                return this.engine;

            EngineConfig config = new EngineConfig();
            config.getAppContext().put(this.exposedSpringApplicationContextKey, this.context);
            config.setLogConfig(null != this.logDirectory ? this.logDirectory.getAbsolutePath() : null, this.logLevel);
            config.setResourcePath(getPath("Resources"));
            String s = getPath("Resources");
//            config.setResourcePath("C:\\Users\\Stanley\\Documents\\BIRT Examples\\birt-test\\spring-birt-master\\spring-birt-integration-example\\src\\main\\webapp\\Resources");
            
            
            try {
                Platform.startup(config);
            } catch (BirtException e) {
                throw new RuntimeException("Could not start the BIRT engine.", e);
            }
            IReportEngineFactory factory = (IReportEngineFactory) Platform.createFactoryObject(IReportEngineFactory.EXTENSION_REPORT_ENGINE_FACTORY);
            this.engine = factory.createReportEngine(config);
        }
        return this.engine;
    }

    public Class<?> getObjectType() {
        return IReportEngine.class;
    }

    public void afterPropertiesSet() throws Exception {
        // log directory
        if (null != logDirectory) {
            Assert.isTrue(logDirectory.isDirectory(), "the path given must be a directory");
            Assert.isTrue(logDirectory.exists(), "the path specified must exist");
        }
        // required properties
        Assert.notNull(exposedSpringApplicationContextKey, "you must provide a valid value for the 'exposedSpringApplicationContextKey' attribute");
    }
    

    /**
     * Get the resource path for Apache applications
     * Example: %DOCUMENTS%\workspace-sts-3.9.4.RELEASE\.metadata\.plugins\org.eclipse.wst.server.core\tmp0\wtpwebapps\spring-birt-integration-example
     * 
     * @param folderName
     * @return
     */
    public String getPath(String folderName) {
        try {
            String path = this.getClass().getClassLoader().getResource("").getPath();
            String fullPath = URLDecoder.decode(path, "UTF-8");
            String pathArr[] = fullPath.split("/WEB-INF/classes/");
            fullPath = pathArr[0];
            String reponsePath = "";
            // to read a file from webcontent
            reponsePath = new File(fullPath).getPath() + File.separatorChar + folderName;
            return reponsePath;
        } catch (Exception e) {
          
        }
        return "";
    }
}
