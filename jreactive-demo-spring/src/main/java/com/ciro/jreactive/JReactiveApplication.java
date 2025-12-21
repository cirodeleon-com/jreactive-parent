package com.ciro.jreactive;

import com.ciro.jreactive.router.Param;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class JReactiveApplication {

    public static void main(String[] args) {
    	//JsoupComponentEngine.installAsDefault();
    	
        SpringApplication.run(JReactiveApplication.class, args);
    }

}
