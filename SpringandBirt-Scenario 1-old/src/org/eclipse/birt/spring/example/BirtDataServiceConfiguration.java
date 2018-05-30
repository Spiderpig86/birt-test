package org.eclipse.birt.spring.remoting.example;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;


@Configuration
public class BirtDataServiceConfiguration {

	@Bean 
	public CarService carService(){ 
		 
		return  new CarServiceImpl();
	}

	@Bean 
	public HttpInvokerServiceExporter myServiceExporter(){ 
		HttpInvokerServiceExporter hse = new HttpInvokerServiceExporter();
		hse.setService( this.carService()) ;
		hse.setServiceInterface( CarService.class); 
		return hse; 
	}

	@Bean
	public SimpleUrlHandlerMapping myUrlMapping(){

		SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();         
		Map urlMap = new HashMap();         
		urlMap.put("/carService", myServiceExporter());                  
		mapping.setUrlMap(urlMap);         
		mapping.setAlwaysUseFullPath(true);         
		return mapping; 		
	}


}