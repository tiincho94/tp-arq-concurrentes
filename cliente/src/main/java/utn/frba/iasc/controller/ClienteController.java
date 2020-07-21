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
				String responseSt = "Gané la subasta "+id+" :)";
				System.out.println(responseSt);
				HttpHeaders respHeaders = new HttpHeaders();
				respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
				return new ResponseEntity<String>(responseSt, respHeaders, HttpStatus.OK);
	}
	
	@GetMapping("/nuevaSubasta")	
	ResponseEntity<?> nuevaSubasta (
			@PathParam("id") String id
			) throws Exception{
				String responseSt = "Me invitaron a la subasta "+id+" :)";
				System.out.println(responseSt);
				HttpHeaders respHeaders = new HttpHeaders();
				respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
				return new ResponseEntity<String>(responseSt, respHeaders, HttpStatus.OK);
	}

	@GetMapping("/subastaPerdida")	
	ResponseEntity<?> subastaPerdida (
			@PathParam("id") String id
			) throws Exception{
				String responseSt = "Perdí la la subasta "+id+" :(";
				System.out.println(responseSt);
				HttpHeaders respHeaders = new HttpHeaders();
				respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
				return new ResponseEntity<String>(responseSt, respHeaders, HttpStatus.OK);
	}
	
	@GetMapping("/nuevoPrecio")	
	ResponseEntity<?> subastaPerdida (
			@PathParam("id") String id,
			@PathParam("precio") Double precio
			) throws Exception{
				String responseSt = "El nuevo precio de la subasta "+id+" es "+String.valueOf(precio);
				System.out.println(responseSt);
				HttpHeaders respHeaders = new HttpHeaders();
				respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
				return new ResponseEntity<String>(responseSt, respHeaders, HttpStatus.OK);
	}
	
	@GetMapping("/subastaCancelada")	
	ResponseEntity<?> subastaCancelada (
			@PathParam("id") String id
			) throws Exception{
				String responseSt = "La subasta "+id+" fue cancelada ";
				System.out.println(responseSt);
				HttpHeaders respHeaders = new HttpHeaders();
				respHeaders.setCacheControl("must-revalidate, post-check=0, pre-check=0");
				return new ResponseEntity<String>(responseSt, respHeaders, HttpStatus.OK);
	}

}
