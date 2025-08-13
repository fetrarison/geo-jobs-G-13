package app.bpartners.geojobs.entity.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ecoute les PolygonFusionEvent et gere le post-traitement.
 */
@Slf4j
@Component
public class PolygonFusionEventListener {
    @EventListener
    public void handleFusionEvent(PolygonFusionEvent event) {
        log.info("Fusion terminée! Résultat: {}", event.getFusionResult());
    }
}
