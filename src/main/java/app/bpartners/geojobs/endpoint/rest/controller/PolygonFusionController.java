package app.bpartners.geojobs.endpoint.rest.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import app.bpartners.geojobs.service.PolygonContinue.PolygonContinueService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@AllArgsConstructor
@RestController
public class PolygonFusionController {

    private final PolygonContinueService polygonContinueService;

    @PostMapping("/fusionner")
    public CompletableFuture<ResponseEntity<Map<String, String>>> fusionner(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucket") String bucket,
            @RequestParam("key") String outputKey
    ) throws IOException {
        return polygonContinueService.fusionnerPolygonesAsync(file, bucket, outputKey)
                .thenApply(ResponseEntity::ok);
    }
}
