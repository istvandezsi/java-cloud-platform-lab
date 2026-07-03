package hu.dezsi.cloudlab;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    private final Counter helloRequests;

    public HelloController(MeterRegistry meterRegistry) {
        this.helloRequests = Counter.builder("hello.requests")
                .description("Number of calls to the hello endpoint")
                .register(meterRegistry);
    }

    @GetMapping("/hello")
    Greeting hello() {
        helloRequests.increment();

        return new Greeting("Hello from Java Cloud Platform Lab");
    }

    record Greeting(String message) {
    }
}
