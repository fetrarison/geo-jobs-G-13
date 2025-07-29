package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.service.PolygonContinue.PolygonContinueService;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/fusionner")
@AllArgsConstructor
public class PolygonFusionController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String fusionner(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucket") String bucket,
            @RequestParam("key") String outputKey,
            PolygonContinueService service
    ) throws IOException {
        File inputFile = service.createTempFileFromMultipart(file, "input", ".geojson");
        File outputFile = service.processAndMergePolygons(inputFile);
        return service.uploadToS3(outputFile, bucket, outputKey);
    }
}