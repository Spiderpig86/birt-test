1 - copy servlet-api.jar to the appserverjar directory
2 - Download Spring and BIRT runtime.
3 - Copy jars from birt-runtime-3_7_1\ReportEngine\lib to WEB-INF\lib
4 - Copy Spring jars 
•	cglib-nodep-2.2.2.jar
•	org.springframework.aop-3.1.0.RELEASE.jar
•	org.springframework.asm-3.1.0.RELEASE.jar
•	org.springframework.beans-3.1.0.RELEASE.jar
•	org.springframework.context.support-3.1.0.RELEASE.jar
•	org.springframework.context-3.1.0.RELEASE.jar
•	org.springframework.core-3.1.0.RELEASE.jar
•	org.springframework.expression-3.1.0.RELEASE.jar
•	org.springframework.web.servlet-3.1.0.RELEASE.jar
•	org.springframework.web-3.1.0.RELEASE.jar
two Web-inf/lib
5 - Run ant deploy.

This will create 
springandbirt.war which you can then deploy to your appserver.  After deploying the war open a browser and enter the 
following url: 
http://localhost:8080/springandbirt/
Run either of the examples.