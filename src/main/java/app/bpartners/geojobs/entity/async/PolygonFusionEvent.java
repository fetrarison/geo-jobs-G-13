package app.bpartners.geojobs.entity.async;

import java.util.Map;

/**
 * Evenement representant le resultat d'une operation de fusion de polygones.
 */
public class PolygonFusionEvent {
    private final Map<String, String> fusionResult;

    public PolygonFusionEvent(Map<String, String> fusionResult) {
        this.fusionResult = fusionResult;
    }

    public Map<String, String> getFusionResult() {
        return fusionResult;
    }
}
