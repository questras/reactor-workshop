package com.nurkiewicz.webflux.demo.emojis;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toMap;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

@RestController
public class EmojiController {

    private final URI emojiTrackerUrl;
    private final WebClient webClient;

    public EmojiController(@Value("${emoji-tracker.url}") URI emojiTrackerUrl, WebClient webClient) {
        this.emojiTrackerUrl = emojiTrackerUrl;
        this.webClient = webClient;
    }

    @GetMapping(value = "/emojis/raw", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent> raw() {
        return retrieve()
            .bodyToFlux(ServerSentEvent.class);
    }

    @GetMapping(value = "/emojis/rps", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Long> rps() {
        return retrieve()
            .bodyToFlux(ServerSentEvent.class)
            .window(Duration.ofSeconds(1))
            .flatMap(Flux::count);
    }

    @GetMapping(value = "/emojis/eps", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Integer> eps() {
        return retrieve()
            .bodyToFlux(new ParameterizedTypeReference<Map<String, Integer>>() {})
            .concatMapIterable(Map::values)
            .window(Duration.ofSeconds(1))
            .flatMap(win -> win.reduce(Integer::sum));
    }

    @GetMapping(value = "/emojis/aggregated", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Map<String, Integer>> aggregated() {
        return retrieve()
            .bodyToFlux(new ParameterizedTypeReference<Map<String, Integer>>() {})
            .concatMapIterable(Map::entrySet)
            .scan(new HashMap<>(), ( acc, entry) -> {
                final HashMap<String, Integer> result = new HashMap<>(acc);
                result.merge(entry.getKey(), entry.getValue(), Integer::sum);
                return result;
            });
    }

    /**
     * @see #topValues(Map, int)
     */
    @GetMapping(value = "/emojis/top", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Map<String, Integer>> top(@RequestParam(defaultValue = "10", required = false) int limit) {
        return aggregated()
            .map(agg -> topValues(agg, limit))
            .distinctUntilChanged();
    }

    private Map<String, Integer> topValues(Map<String, Integer> agg, int n) {
        return new HashMap<>(agg
                .entrySet()
                .stream()
                .sorted(comparing(Map.Entry::getValue, reverseOrder()))
                .limit(n)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @GetMapping(value = "/emojis/topStr", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<String> topStr(@RequestParam(defaultValue = "10", required = false) int limit) {
        return top(limit)
            .map(this::keysAsOneString)
                .distinctUntilChanged();
    }

    String keysAsOneString(Map<String, Integer> m) {
        return m
            .keySet()
            .stream()
            .map(EmojiController::codeToEmoji)
            .collect(Collectors.joining());
    }

    private WebClient.ResponseSpec retrieve() {
        return webClient
            .get()
            .uri(emojiTrackerUrl)
            .retrieve();
    }

    static String codeToEmoji(String hex) {
        final String[] codes = hex.split("-");
        if (codes.length == 2) {
            return hexToEmoji(codes[0]) + hexToEmoji(codes[1]);
        } else {
            return hexToEmoji(hex);
        }
    }

    private static String hexToEmoji(String hex) {
        return new String(Character.toChars(Integer.parseInt(hex, 16)));
    }

}