package app.bpartners.geojobs.service.PolygonContinue;

import app.bpartners.geojobs.entity.PolygonFusionRequestedEvent;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

@Component
public class PolygonFusionEventListener {
    @Autowired
    private PolygonContinueService service;

    @Async
    @EventListener
    @SneakyThrows
    public void handleFusionEvent(PolygonFusionRequestedEvent event) throws IOException {
        // Utilisation de la réflexion pour invoquer la méthode (exemple)
        Method method = PolygonContinueService.class.getMethod("processAndMergePolygons", File.class);
        File inputFile = service.createTempFileFromMultipart(event.getFile(), "input", ".geojson");
        File outputFile = (File) method.invoke(service, inputFile);
        service.uploadToS3(outputFile, event.getBucket(), event.getOutputKey());
        // Vous pouvez aussi publier un nouvel événement: PolygonFusionCompletedEvent
    }
}
