package iasc.g4.tester;

import javax.websocket.server.PathParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuctionClientController {

	private static final Logger LOG = LoggerFactory.getLogger(AuctionClientController.class);

	@Autowired
	private AuctionService service;

	@GetMapping("/subastaGanada")
	public ResponseEntity<String> subastaGanada(@PathParam("id") String id) {
		LOG.info("Gané la subasta {} :)!", id);
		service.removeAuction(id);
		return ResponseEntity.ok().body("Ok!");
	}

	@GetMapping("/nuevaSubasta")
	public ResponseEntity<String> nuevaSubasta(@PathParam("id") String id) {
		LOG.info("Me invitaron a la subasta {} :)", id);
		service.receiveInvitation(id);
		return ResponseEntity.ok().body("Ok!");
	}

	@GetMapping("/subastaPerdida")
	public ResponseEntity<String> subastaPerdida(@PathParam("id") String id) {
		LOG.info("Perdí la subasta {} :(", id);
		service.removeAuction(id);
		return ResponseEntity.ok().body("Ok!");
	}

	@GetMapping("/nuevoPrecio")
	public ResponseEntity<String> subastaPerdida(@PathParam("id") String id, @PathParam("precio") Double precio) {
		LOG.info("Nuevo precio recibido para subasta {}: {}", id, precio);
		service.updatePrice(id, precio);
		return ResponseEntity.ok().body("Ok!");
	}

	@GetMapping("/subastaCancelada")
	public ResponseEntity<String> subastaCancelada(@PathParam("id") String id) {
		LOG.info("La subasta {} fue cancelada", id);
		service.removeAuction(id);
		return ResponseEntity.ok().body("Ok!");
	}

}
