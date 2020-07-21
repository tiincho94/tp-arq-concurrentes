package utn.frba.iasc.controller;

import javax.websocket.server.PathParam;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClienteController {

	@GetMapping("/{message}")	
	public ResponseEntity<String> recibirNotificacion(@PathParam("id") String id, @PathParam("message") String message) {
		String resp = "Mensaje: " + message + "; Subasta: "+ id;
		System.out.println(resp);
		HttpHeaders respHeaders = new HttpHeaders();
		respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>("Mensaje: " + resp + "; Subasta: "+ id, respHeaders, HttpStatus.OK);
	}
}
