package utn.frba.iasc.controller;

import javax.websocket.server.PathParam;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClienteController {
	
	@GetMapping("/subastaGanada")	
	ResponseEntity<?> subastaGanada (
			@PathParam("id") String id
			) throws Exception{
				String responseSt = "Gané la subasta "+id;
				System.out.println(responseSt);
				HttpHeaders respHeaders = new HttpHeaders();
				respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
				return new ResponseEntity<String>(responseSt, respHeaders, HttpStatus.OK);
	}
	
	

}
