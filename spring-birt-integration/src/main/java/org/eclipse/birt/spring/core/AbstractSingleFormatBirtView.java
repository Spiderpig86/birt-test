package org.eclipse.birt.spring.core;

import java.io.FileInputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.eclipse.birt.report.data.oda.jdbc.IConnectionFactory;
import org.eclipse.birt.report.engine.api.EngineConstants;
import org.eclipse.birt.report.engine.api.IEngineTask;
import org.eclipse.birt.report.engine.api.IGetParameterDefinitionTask;
import org.eclipse.birt.report.engine.api.IHTMLActionHandler;
import org.eclipse.birt.report.engine.api.IParameterDefnBase;
import org.eclipse.birt.report.engine.api.IRenderOption;
import org.eclipse.birt.report.engine.api.IRenderTask;
import org.eclipse.birt.report.engine.api.IReportDocument;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.IRunTask;
import org.eclipse.birt.report.engine.api.IScalarParameterDefn;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.engine.api.ReportParameterConverter;
import org.eclipse.birt.report.model.api.IModuleOption;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.view.AbstractUrlBasedView;

/**
 * The main driver for rendering reports, configuring engines, and loading from
 * external sources if applicable.
 *
 */
public abstract class AbstractSingleFormatBirtView extends AbstractUrlBasedView {

  /**
   * Interface for any object to help resolve paths for report
   *
   */
  public static interface BirtViewResourcePathCallback {

    /**
     * Note that these are all getters and setters
     */
    String baseImageUrl(ServletContext sc, HttpServletRequest r, String reportName) throws Throwable;

    String baseUrl(ServletContext sc, HttpServletRequest r, String reportName) throws Throwable;

    String pathForReport(ServletContext servletContext, HttpServletRequest r, String reportName) throws Throwable;

    String imageDirectory(ServletContext sc, HttpServletRequest request, String reportName);

    String resourceDirectory(ServletContext sc, HttpServletRequest request, String reportName);

    String pathForDocument(ServletContext servletContext, HttpServletRequest r, String documentName) throws Throwable;

  }

  /* Render methods */
  public static final int RUNRENDER = 0;
  public static final int RUNTHENRENDER = 1;

  /* Engine properties */
  private DataSource dataSource;
  private String reportName;
  private IReportEngine birtEngine; // The engine itself

  /* Default properties */
  private int taskType = RUNRENDER;
  private String reportNameRequestParameter = "reportName";
  private String documentNameRequestParameter = "documentName";
  private String imagesDir = "images";
  private String documentsDir = ""; // TODO may not be needed
  private String documentName = null; // TODO may not be needed
  private String reportsDir = "";
  private String resourcesDir = "resources";

  private String isNullParameterName = "__isnull";
  private IRenderOption renderOption; // Option for rendering HTML or PDF for example
  private String reportFormatRequestParameter = "reportFormat";
  protected BirtViewResourcePathCallback birtViewResourcePathCallback; // Used for resolving paths to static resources

  protected IHTMLActionHandler actionHandler; // Action handler used for rendering reports
  private String requestEncoding = "UTF-8";
  private String renderRange = null; // Param for number of elements to render
  private String reportOutputFormat = "html";

  private Map<String, Object> reportParameters = new HashMap<String, Object>(); // Parameters for engine rendering

  private boolean closeDataSourceConnection = true; // IConnectionFactory.CLOSE_PASS_IN_CONNECTION

  protected abstract RenderOption renderReport(Map<String, Object> map, HttpServletRequest request,
      HttpServletResponse response, BirtViewResourcePathCallback resourcePathCallback,
      Map<String, Object> appContextValuesMap, String reportName, String format, IRenderOption options)
      throws Throwable;

  /**
   * InitializingBean method to validate state of object (checking validity of parameters)
   * 
   * @throws Exception
   */
  public void afterPropertiesSet() throws Exception {
    if (isNullOrWhitespace(this.requestEncoding))
      throw new Exception("requestEncoding is not set");

    if (isNullOrWhitespace(this.reportFormatRequestParameter))
      throw new Exception("reportFormatRequestParameter is not set");

    if (isNullOrWhitespace(this.reportName) && isNullOrWhitespace(this.reportNameRequestParameter))
      throw new Exception("Either reportName or reportNameRequestParameter must be set");

    if (isNullOrWhitespace(this.documentNameRequestParameter))
      throw new Exception("documentNameRequestParameter is not set");

    // Set fields if needed
    if (this.renderOption == null)
      this.renderOption = new RenderOption();

    if (this.actionHandler == null)
      this.actionHandler = new SimpleRequestParameterActionHandler(this.reportNameRequestParameter,
          this.reportFormatRequestParameter); // Default class to manage any additional actions for generating reports

    if (this.birtViewResourcePathCallback == null)
      this.birtViewResourcePathCallback = new SimpleBirtViewResourcePathCallback(this.reportsDir, this.imagesDir,
          this.resourcesDir, this.documentsDir); // Default object for resolving resource paths

  }

  /**
   * Method needed for AbstractUrlBasedView for rendering a report
   */
  @Override
  @SuppressWarnings("unchecked")
  protected void renderMergedOutputModel(Map<String, Object> modelData, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    FileInputStream fis = null;
    IReportRunnable runnable = null;
    IReportDocument document = null;
    try {
      // If the parameters are unset
      if (this.reportParameters == null)
        this.reportParameters = new HashMap<String, Object>();

      // Clone from modelData
      for (String k : modelData.keySet())
        this.reportParameters.put(k, modelData.get(k));

      // Get full canonicalized names for report and doc
      String fullReportName = getFullName(this.reportName, this.reportNameRequestParameter, request);
      String fullDocumentName = getFullName(this.documentName, this.documentNameRequestParameter, request);

      if (documentName == null)
        fullDocumentName = reportName.replaceAll(".rptdesign", ".rptdocument");

      // Get format for rendering report
      String format;
      if (this.reportOutputFormat != null)
        format = this.reportOutputFormat;
      else
        format = request.getParameter(this.reportFormatRequestParameter);

      ServletContext sc = request.getServletContext(); // Avoid creating another HTTP session if possible

      if (format == null)
        format = "html"; // Default rendering format

      Map<String, Object> mapOfOptions = new HashMap<>();
      mapOfOptions.put(IModuleOption.RESOURCE_FOLDER_KEY,
          birtViewResourcePathCallback.resourceDirectory(sc, request, reportName));
      mapOfOptions.put(IModuleOption.PARSER_SEMANTIC_CHECK_KEY, Boolean.FALSE);

      // Set content type of report
      String contentType = birtEngine.getMIMEType(format);
      response.setContentType(contentType);
      setContentType(contentType);

      Map<String, Object> appContextMap = new HashMap<>();
      appContextMap.put(EngineConstants.APPCONTEXT_BIRT_VIEWER_HTTPSERVET_REQUEST, request);

      /*
       * If the given data source is not null, pull data from that source instead of
       * relying on the one in the report
       */
      if (this.dataSource != null) {
        appContextMap.put(IConnectionFactory.PASS_IN_CONNECTION, this.dataSource.getConnection());

        if (this.closeDataSourceConnection)
          appContextMap.put(IConnectionFactory.CLOSE_PASS_IN_CONNECTION, Boolean.TRUE);
      }

      IEngineTask task = null;
      String pathForReport = birtViewResourcePathCallback.pathForReport(sc, request, fullReportName);
      fis = new FileInputStream(pathForReport);
      runnable = birtEngine.openReportDesign(fullReportName, fis, mapOfOptions);

      /* Determine render method for the report */
      if (runnable != null && this.taskType == AbstractSingleFormatBirtView.RUNRENDER) {
        task = birtEngine.createRunAndRenderTask(runnable);
        IRunAndRenderTask runAndRenderTask = (IRunAndRenderTask) task;
        IRenderOption options = null == this.renderOption ? new RenderOption() : this.renderOption;
        options.setActionHandler(actionHandler);
        IRenderOption returnedRenderOptions = renderReport(modelData, request, response,
            this.birtViewResourcePathCallback, appContextMap, reportName, format, options);
        for (String k : appContextMap.keySet())
          runAndRenderTask.getAppContext().put(k, appContextMap.get(k));

        runAndRenderTask.setRenderOption(returnedRenderOptions);
        runAndRenderTask.run();
        runAndRenderTask.close();
      } else {

        // Run then Render
        if (runnable != null) {
          task = birtEngine.createRunTask(runnable);
          IRunTask runTask = (IRunTask) task;
          for (String k : appContextMap.keySet())
            runTask.getAppContext().put(k, appContextMap.get(k));
          String pathForDocument = birtViewResourcePathCallback.pathForDocument(sc, request, fullDocumentName);
          runTask.run(pathForDocument);
          runTask.close();
          document = birtEngine.openReportDocument(fullDocumentName, pathForDocument, mapOfOptions);
          task = birtEngine.createRenderTask(document);
          IRenderTask renderTask = (IRenderTask) task;
          IRenderOption options = null == this.renderOption ? new RenderOption() : this.renderOption;
          options.setActionHandler(actionHandler);
          IRenderOption returnedRenderOptions = renderReport(modelData, request, response,
              this.birtViewResourcePathCallback, appContextMap, reportName, format, options);
          for (String k : appContextMap.keySet())
            renderTask.getAppContext().put(k, appContextMap.get(k));
          if (renderRange != null) {

            renderTask.setPageRange(renderRange);
          }
          renderTask.setRenderOption(returnedRenderOptions);
          renderTask.render();
          renderTask.close();
          document.close();
        }
      }

    } catch (Throwable th) {
      throw new RuntimeException(th); // nothing useful to do here
    } finally {
      // Cleanup
      if (null != fis)
        IOUtils.closeQuietly(fis);
      if (null != document)
        document.close();
    }
  }
  /**
   * Check if the requested parameter key exists (name param acts as the value if it has no key associated with it)
   * @param request
   * @param name
   * @return
   * @throws Throwable
   */
  private boolean doesReportParameterExist(HttpServletRequest request, String name) throws Throwable {
    if (request == null)
      throw new Exception("Param HttpServletRequest request must be non-null");

    if (!StringUtils.hasText(name))
      throw new Exception("The parameter name must be specified");

    Map<String, String[]> paramMap = request.getParameterMap();
    Set<String> nullParams = getParameterValues(request, isNullParameterName); // Get all the values that have no name
                                                                               // associated with it (denoted with
                                                                               // __is_null)
    boolean exists = false;

    if (paramMap != null)
      exists = paramMap.containsKey(name);

    if (nullParams.contains(name))
      exists = true;

    return exists;
  }

  /**
   * Wrapper method to return all parameter values in a set for a specific
   * parameter name instead of an array
   * 
   * @param request
   * @param parameterName
   * @return
   * @throws Throwable
   */
  public Set<String> getParameterValues(HttpServletRequest request, String parameterName) throws Throwable {
    handleEncodingInRequest(request);
    String[] parameterValuesArray = request.getParameterValues(parameterName);
    Set<String> parameterValues = new LinkedHashSet<>();
    Collections.addAll(parameterValues, parameterValuesArray == null ? new String[0] : parameterValuesArray);
    return parameterValues;
  }

  /**
   * If no encoding was specified, set it to the default encoding (UTF-8)
   * 
   * @param request
   * @throws Throwable
   */
  private void handleEncodingInRequest(HttpServletRequest request) throws Throwable {
    if (!StringUtils.hasText(request.getCharacterEncoding()))
      request.setCharacterEncoding(this.requestEncoding);
  }

  /**
   * Wrap encoding handling and fetching parameter value from the request in one
   * method
   * 
   * @param request
   * @param parameterName
   * @return
   */
  private String getParameter(HttpServletRequest request, String parameterName) throws Throwable {
    handleEncodingInRequest(request);
    String result = request.getParameter(parameterName); // Get the value for the parameter
    return StringUtils.hasText(result) ? result : "";
  }

  /**
   * HELPER FUNCTIONS
   */
  public String getReportName() {
    return reportName;
  }

  public void setReportName(String reportName) {
    this.reportName = reportName;
  }

  /**
   * Set callback that helps resolve directories for reports, documents, images,
   * and other resources
   * 
   * @param birtViewResourcePathCallback
   */
  public void setBirtViewResourcePathCallback(BirtViewResourcePathCallback birtViewResourcePathCallback) {
    this.birtViewResourcePathCallback = birtViewResourcePathCallback;
  }

  /**
   * Used in report's app context to fetch data if specified (otherwise it would
   * predefined data in report)
   */
  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * This method allows you to set a report parameter to a null value
   */
  public void setNullParameterName(String nullParameterName) {
    isNullParameterName = nullParameterName;
  }

  /**
   * Method to set report Parameters, defaults form the URL
   */
  public void setReportParameters(Map<String, Object> reportParameters) {
    this.reportParameters = reportParameters;
  }

  /**
   * Method to set encoding for the request
   */
  public void setRequestEncoding(String r) {
    this.requestEncoding = r;
  }

  /**
   * Set the instance of the BIRT Engine
   */
  public void setBirtEngine(IReportEngine birtEngine) {
    this.birtEngine = birtEngine;
  }

  /**
   * Set the resource directory that contains BIRT libs, styles, reports, and
   * other static files
   */
  public void setResourceDirectory(String resourceDirectory) {
    this.resourcesDir = resourceDirectory;
  }

  /**
   * Set the folder within the web app that will contain reports
   */
  public void setReportsDirectory(String reportsDirectory) {
    this.reportsDir = reportsDirectory;
  }

  /**
   * by default this parameter is set to reportFomat (eg reportFormat = html), but
   * using this method the request parameter can be changed.
   */
  public void setReportFormatRequestParameter(String rf) {
    this.reportFormatRequestParameter = rf;
  }

  /**
   * by default this parameter is set to reportName (eg reportName =
   * TopNPercent.rptdesign), but using this method the request parameter can be
   * changed.
   */
  public void setReportNameRequestParameter(String rn) {
    this.reportNameRequestParameter = rn;
  }

  /**
   * by default this parameter is set to nameofreport.rptdocument but using this
   * method the name of the rptdocument can be set
   */
  public void setDocumentName(String dn) {
    this.documentName = dn;
  }

  /**
   * by default this parameter is set to documentName (eg documentName =
   * TopNPercent.rptdocument but using this method the name of the requestor
   * parameter can be change
   */
  public void setDocumentNameRequestParameter(String dn) {
    this.documentNameRequestParameter = dn;
  }

  /**
   * Set the images directory that engine will use to generate temporary images
   * for the reports by default the images directory will be used
   */
  public void setImagesDirectory(String imagesDir) {
    this.imagesDir = imagesDir;
  }

  /**
   * Set the documents directory that engine will use to generate temporary
   * rptdocuments for the reports by default the documents directory will be used
   */
  public void setDocumentsDirectory(String documentDir) {
    this.documentsDir = documentDir;

  }

  /**
   * Used to set different engine modes: 1. Run_Render - run and render a report
   * with a single task 2. Run_Then_Render - generate the rptdocument and then
   * render it to the user
   */
  public void setTaskType(int taskType) {
    this.taskType = taskType;

  }

  /**
   * Set the page range string for reports that use a run then render task. eg
   * '1-3' or '1,3,4'
   */
  public void setRenderRange(String renderRange) {
    this.renderRange = renderRange;

  }

  /**
   * Sets the Action Handler instance to be used when generating HTML reports (used for populating reports with data, links, etc)
   * SimpleRequestParameterActionHandler is used by default.
   */
  public void setHtmlActionHandler(IHTMLActionHandler actionHandler) {
    this.actionHandler = actionHandler;
  }

  /**
   * This method allows setting the render options for rendering reports
   *
   * @param renderOption
   */
  public void setRenderOption(IRenderOption renderOption) {
    this.renderOption = renderOption;
  }

  public static boolean isNullOrWhitespace(String value) {
    return value == null || value.trim().isEmpty();
  }

  /**
   * Adds .rptdesign suffix if missing from given design file name
   * @param name
   * @return
   */
  private String canonicalizeName(String name) {
    if (!StringUtils.hasText(reportName))
      return null; // Name not set

    return !reportName.toLowerCase().endsWith(".rptdesign") ? reportName + ".rptdesign" : reportName;
  }

  /**
   * Adds .rptdocument suffix if missing from given document file name
   * @param docName
   * @return
   */
  private String canonicalizeDocName(String docName) {
    if (!StringUtils.hasText(docName))
      return null;

    return !docName.toLowerCase().endsWith(".rptdocument") ? docName + ".rptdocument" : docName;
  }

  /**
   * Generates a full canonicalized name for a given report or doc name
   * 
   * @param name
   * @param request
   * @return
   */
  private String getFullName(String name, String requestParameter, HttpServletRequest request) {
    String temp = StringUtils.hasText(name) ? this.reportName : request.getParameter(requestParameter);
    return canonicalizeName(temp);
  }

  public void setCloseDataSourceConnection(boolean b) {
    this.closeDataSourceConnection = b;
  }

  public BirtViewResourcePathCallback getBirtViewResourcePathCallback() {
    return this.birtViewResourcePathCallback;
  }

  /**
   * Used to resolve directories for fetching assets to render reports
   */
  public class SimpleBirtViewResourcePathCallback implements AbstractSingleFormatBirtView.BirtViewResourcePathCallback {

    private String reportFolder, imagesFolder, resourceFolder, documentsFolder;

    public String baseImageUrl(ServletContext sc, HttpServletRequest request, String reportName) throws Throwable {
      return request.getContextPath() + "/" + imagesFolder;
    }

    public String baseUrl(ServletContext sc, HttpServletRequest request, String reportName) throws Throwable {
      String baseUrl = request.getRequestURI();
      int trimloc = baseUrl.lastIndexOf("/", baseUrl.length() - 2);
      return baseUrl.substring(0, trimloc);
    }

    public String pathForReport(ServletContext sc, HttpServletRequest request, String reportName) throws Throwable {
      String folder = sc.getRealPath(reportFolder);

      if (!folder.endsWith("/"))
        folder = folder + "/";

      return folder + reportName;
    }

    public String pathForDocument(ServletContext sc, HttpServletRequest request, String documentName) throws Throwable {

      String folder = sc.getRealPath(documentsFolder);

      if (!folder.endsWith("/"))
        folder = folder + "/";

      return folder + documentName;
    }

    public String imageDirectory(ServletContext sc, HttpServletRequest request, String reportName) {
      return sc.getRealPath(imagesFolder);
    }

    public String resourceDirectory(ServletContext sc, HttpServletRequest request, String reportName) {
      return sc.getRealPath(resourceFolder);
    }

    public SimpleBirtViewResourcePathCallback(String reportFolder, String imagesFolder, String resourcesFolder, String documentsFolder) {
      this.reportFolder = StringUtils.hasText(reportFolder) ? reportFolder : "";
      this.imagesFolder = StringUtils.hasText(imagesFolder) ? imagesFolder : "";
      this.resourceFolder = StringUtils.hasText(resourcesFolder) ? resourcesFolder : "";
      this.documentsFolder = StringUtils.hasText(documentsFolder) ? documentsFolder : "";
    }
  }

}