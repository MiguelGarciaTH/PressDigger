package arquivo.controller;

import arquivo.model.Person;
import arquivo.repository.PersonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/persons")
public class PersonsController {

    private final PersonRepository personRepository;

    public PersonsController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @GetMapping("/count")
    public long countPersons() {
        return personRepository.count();
    }

    @GetMapping("/find")
    public Page<Person> find(Pageable pageable) {
        return personRepository.findAll(pageable);
    }
}
