package hu.dezsi.cloudlab;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    Greeting hello() {
        return new Greeting("Hello from Java Cloud Platform Lab");
    }

    record Greeting(String message) {
    }
}
