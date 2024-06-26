package com.cs.uploader.controller;

import com.cs.uploader.model.Event;
import com.cs.uploader.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Objects;

/**
 * @author Max.Alencar
 */
@RestController
public class EventController {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<Event> getAllEvents() {
        logger.info("Getting all events...");
        return service.findAll();
    }

    @PostMapping()
    public Mono<ResponseEntity<Void>> upload(@RequestPart("file") FilePart part) {
        logger.info("Uploading file {}...", part.filename());

        ObjectMapper mapper = new ObjectMapper();
        StringDecoder decoder = StringDecoder.allMimeTypes();

        Flux<String> output = decoder.decode(part.content(), ResolvableType.forClass(String.class), null, Collections.emptyMap());

        Flux<GroupedFlux<String, Event>> events = output.sort().map(x -> {
                    return readValue(mapper, x);
                })
                .groupBy(Event::getId);

        events.subscribe(groupedFlux -> {
            groupedFlux.collectList().doOnNext(list -> {
                if (list.size() == 2) {
                    Event eventFinished = list.get(0);
                    Event eventOpen = list.get(1);

                    if (!Objects.equals(eventFinished.getState(), eventOpen.getState())) {
                        eventFinished.setTimestamp(eventFinished.getTimestamp() - eventOpen.getTimestamp());
                        eventFinished.setAlert(eventFinished.getTimestamp() > 4);

                        logger.info("Saving Event: {}", eventFinished);
                        Mono<Event> eventMono = service.save(eventFinished);
                        logger.info("Event Saved: {}", eventMono.log());
                    }
                }
            }).subscribe(list -> {
                logger.info("Saving Event ID: {}, events: {}", groupedFlux.key(), list);
            });
        });

        return Mono.just(ResponseEntity.ok().<Void>build());
    }

    private Event readValue(ObjectMapper mapper, String x) {
        try {
            return mapper.readValue(x, Event.class);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
