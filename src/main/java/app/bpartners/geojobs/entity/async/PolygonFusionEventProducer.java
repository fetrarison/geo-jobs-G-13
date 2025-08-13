package app.bpartners.geojobs.entity.async;

import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;


/**
 * Publie un PolygonFusionEvent lorsqu'une operation de fusion de polygones est terminee.
 */
@Component
@AllArgsConstructor
public class PolygonFusionEventProducer {
    private final ApplicationEventPublisher publisher;
    public void publish(PolygonFusionEvent event) {
        publisher.publishEvent(event);
    }
}
