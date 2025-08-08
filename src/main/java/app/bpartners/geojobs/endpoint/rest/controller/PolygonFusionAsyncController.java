package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.entity.PolygonFusionRequestedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController

public class PolygonFusionAsyncController {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> fusionner(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucket") String bucket,
            @RequestParam("key") String outputKey
    ) {
        PolygonFusionRequestedEvent event = new PolygonFusionRequestedEvent(file, bucket, outputKey);
        eventPublisher.publishEvent(event);
        return ResponseEntity.accepted().body("Fusion demandée : elle sera traitée en arrière-plan.");
    }
}
