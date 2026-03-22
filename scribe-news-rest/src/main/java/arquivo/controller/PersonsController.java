package arquivo.controller;

import arquivo.model.Person;
import arquivo.repository.PersonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/persons")
public class PersonsController {

    private final PersonRepository personRepository;

    public PersonsController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @GetMapping("/find")
    public Page<Person> search(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return personRepository.findAll(pageable);
    }
}
